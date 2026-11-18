package com.soubhagya.systivex.order;

import com.soubhagya.systivex.order.api.CheckoutStateUnavailableException;
import com.soubhagya.systivex.order.api.DownstreamUnavailableException;
import com.soubhagya.systivex.order.api.IdempotencyConflictException;
import com.soubhagya.systivex.order.api.InventoryRejectedException;
import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.api.PaymentDeclinedException;
import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.model.OrderStatus;
import com.soubhagya.systivex.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Checkout with an optional client supplied Idempotency-Key.
 *
 * <p>The contract, in one place:
 *
 * <ol>
 *   <li>the key identifies exactly one checkout attempt, and one attempt is
 *       one order row — the order row therefore carries the key digest;
 *   <li>ownership is claimed by inserting that row. The unique index on the
 *       key digest is the arbiter, so exactly one concurrent request can own
 *       a key: the loser gets a unique violation and never reaches a
 *       downstream call;
 *   <li>the same key with the same payload replays the recorded outcome
 *       (success, or the original failure) instead of repeating the
 *       business operation;
 *   <li>the same key with a different payload is a conflict (409) — the key
 *       may not be reused for another operation;
 *   <li>while the owning attempt is still running the key is busy (409), so
 *       no second checkout starts;
 *   <li>a request without a key never reaches this class: it keeps the
 *       pre-existing behaviour, one attempt per request.
 * </ol>
 *
 * <p>Only digests of the key and of the payload are persisted, and neither
 * the key nor the payload appears in a log line, a metric label or a
 * response body.
 */
@Service
public class IdempotentCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentCheckoutService.class);

    private final CheckoutService checkoutService;
    private final OrderRepository orders;
    private final ObservationRegistry observations;
    private final Counter replays;
    private final Counter conflicts;

    public IdempotentCheckoutService(
            CheckoutService checkoutService,
            OrderRepository orders,
            ObservationRegistry observations,
            MeterRegistry meters) {
        this.checkoutService = checkoutService;
        this.orders = orders;
        this.observations = observations;
        // Tag-free, like the rest of the service: the outcome is in the name
        // and no client supplied value ever becomes a label.
        this.replays = meters.counter("checkout.idempotent.replay");
        this.conflicts = meters.counter("checkout.idempotent.conflict");
    }

    /**
     * Runs a checkout for the given key, replaying the recorded outcome when
     * the key has been used before with the same payload.
     */
    public OrderResponse checkout(String rawKey, OrderRequest request) {
        // Same business span as the unkeyed path, so both are observable in
        // the same way. No request payload in attributes.
        return Observation.createNotStarted("checkout", observations)
                .observe(() -> doCheckout(rawKey, request));
    }

    private OrderResponse doCheckout(String rawKey, OrderRequest request) {
        String key = IdempotencyKeyPolicy.requireValid(rawKey);
        String keyHash = IdempotencyFingerprint.keyHash(key);
        String requestHash = IdempotencyFingerprint.requestHash(request);

        Optional<OrderEntity> existing = orders.findByIdempotencyKeyHash(keyHash);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        UUID orderId = UUID.randomUUID();
        OrderEntity order;
        try {
            // The insert is the ownership claim. A concurrent attempt with the
            // same key loses here — before any downstream call is made.
            order = checkoutService.createPendingOrder(orderId, request, keyHash, requestHash);
        } catch (DataIntegrityViolationException concurrentClaim) {
            OrderEntity winner = orders.findByIdempotencyKeyHash(keyHash).orElse(null);
            if (winner == null) {
                // Not a lost race: a genuine storage failure. Let it surface
                // as the controlled storage error it is.
                throw concurrentClaim;
            }
            return replayOrConflict(winner, requestHash);
        }
        return checkoutService.performCheckout(order, request);
    }

    private OrderResponse replayOrConflict(OrderEntity existing, String requestHash) {
        if (!requestHash.equals(existing.getIdempotencyRequestHash())) {
            conflicts.increment();
            log.info("Checkout idempotency conflict: key reused with a different request payload");
            throw new IdempotencyConflictException(
                    IdempotencyConflictException.REASON_KEY_REUSED,
                    "Idempotency-Key was already used with a different request payload");
        }
        if (existing.getStatus() == OrderStatus.CONFIRMED) {
            replays.increment();
            log.info("Checkout {} REPLAYED", existing.getId());
            return OrderResponse.confirmed(
                    existing.getId(),
                    existing.getReservationId(),
                    existing.getAuthorizationId(),
                    existing.getProductId(),
                    existing.getQuantity(),
                    existing.getAmount());
        }
        if (existing.getStatus() == OrderStatus.FAILED) {
            replays.increment();
            log.info("Checkout {} REPLAYED failure", existing.getId());
            throw replayFailure(existing);
        }
        // Still running: never start a second checkout for the same key.
        conflicts.increment();
        log.info("Checkout idempotency conflict: order {} is still pending", existing.getId());
        throw new IdempotencyConflictException(
                IdempotencyConflictException.REASON_IN_PROGRESS,
                "A checkout for this Idempotency-Key is already in progress");
    }

    /**
     * Rebuilds the original failure so the repeated request sees the same
     * classification, the same order id and the same sanitized message the
     * first attempt produced.
     */
    private RuntimeException replayFailure(OrderEntity existing) {
        OrderRequest original =
                new OrderRequest(
                        existing.getProductId(),
                        existing.getQuantity(),
                        existing.getCustomerId(),
                        existing.getAmount());
        String reason = existing.getFailureReason();
        String message = existing.getFailureMessage();
        if (reason == null || message == null) {
            // The recorded outcome is incomplete: it cannot be reproduced
            // faithfully, and repeating the operation could charge twice.
            throw new CheckoutStateUnavailableException(
                    "Stored checkout state could not be reproduced; checkout cannot be replayed");
        }
        if (InventoryRejectedException.REASON.equals(reason)) {
            return new InventoryRejectedException(existing.getId(), original, message);
        }
        if (PaymentDeclinedException.REASON.equals(reason)) {
            return new PaymentDeclinedException(existing.getId(), original, message);
        }
        if (DownstreamUnavailableException.REASON.equals(reason)) {
            return new DownstreamUnavailableException(existing.getId(), original, message);
        }
        throw new CheckoutStateUnavailableException(
                "Stored checkout state could not be reproduced; checkout cannot be replayed");
    }
}
