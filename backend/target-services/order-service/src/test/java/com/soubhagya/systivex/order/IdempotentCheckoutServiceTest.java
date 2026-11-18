package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.order.api.CheckoutStateUnavailableException;
import com.soubhagya.systivex.order.api.IdempotencyConflictException;
import com.soubhagya.systivex.order.api.InventoryRejectedException;
import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.model.OrderStatus;
import com.soubhagya.systivex.order.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The decision table of the idempotent contract, isolated from HTTP and the
 * database: which recorded state produces which answer, and - the part that
 * matters most - that a request which loses the claim race never reaches a
 * downstream call.
 */
class IdempotentCheckoutServiceTest {

    private static final String KEY = "a-client-supplied-key";

    private CheckoutService checkoutService;
    private OrderRepository orders;
    private IdempotentCheckoutService service;

    @BeforeEach
    void setUp() {
        checkoutService = mock(CheckoutService.class);
        orders = mock(OrderRepository.class);
        service =
                new IdempotentCheckoutService(
                        checkoutService,
                        orders,
                        ObservationRegistry.create(),
                        new SimpleMeterRegistry());
    }

    private OrderRequest request() {
        return new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    private OrderEntity storedOrder(OrderStatus status) {
        OrderEntity order =
                new OrderEntity(
                        UUID.randomUUID(),
                        "CUST-1001",
                        "SKU-1001",
                        1,
                        new BigDecimal("1499.00"),
                        IdempotencyFingerprint.keyHash(KEY),
                        IdempotencyFingerprint.requestHash(request()));
        if (status == OrderStatus.CONFIRMED) {
            order.confirm(UUID.randomUUID(), UUID.randomUUID());
        } else if (status == OrderStatus.FAILED) {
            order.fail("INVENTORY_REJECTED", "Inventory reservation rejected for product SKU-1001");
        }
        return order;
    }

    @Test
    void losingTheClaimRaceIsAnsweredWithAnInProgressConflict() {
        OrderEntity winner = storedOrder(OrderStatus.PENDING);
        when(orders.findByIdempotencyKeyHash(anyString()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(checkoutService.createPendingOrder(any(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));

        assertThatThrownBy(() -> service.checkout(KEY, request()))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(exception -> ((IdempotencyConflictException) exception).getReason())
                .isEqualTo(IdempotencyConflictException.REASON_IN_PROGRESS);

        // The loser of the race never runs the business part.
        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void aGenuineStorageFailureIsNotTurnedIntoAConflict() {
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.empty());
        when(checkoutService.createPendingOrder(any(), any(), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("connection lost"));

        assertThatThrownBy(() -> service.checkout(KEY, request()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void aConfirmedOutcomeIsReplayedWithoutASecondAttempt() {
        OrderEntity winner = storedOrder(OrderStatus.CONFIRMED);
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.of(winner));

        OrderResponse response = service.checkout(KEY, request());

        assertThat(response.orderId()).isEqualTo(winner.getId());
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.reservationId()).isEqualTo(winner.getReservationId());
        assertThat(response.authorizationId()).isEqualTo(winner.getAuthorizationId());

        verify(checkoutService, never()).createPendingOrder(any(), any(), anyString(), anyString());
        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void aFailedOutcomeIsReplayedAsTheOriginalFailure() {
        OrderEntity winner = storedOrder(OrderStatus.FAILED);
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.of(winner));

        assertThatThrownBy(() -> service.checkout(KEY, request()))
                .isInstanceOf(InventoryRejectedException.class)
                .hasMessage("Inventory reservation rejected for product SKU-1001")
                .extracting(exception -> ((InventoryRejectedException) exception).getOrderId())
                .isEqualTo(winner.getId());

        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void aDifferentPayloadUnderTheSameKeyIsAConflict() {
        OrderEntity winner = storedOrder(OrderStatus.CONFIRMED);
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.of(winner));

        OrderRequest different =
                new OrderRequest("SKU-1001", 2, "CUST-1001", new BigDecimal("1499.00"));

        assertThatThrownBy(() -> service.checkout(KEY, different))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(exception -> ((IdempotencyConflictException) exception).getReason())
                .isEqualTo(IdempotencyConflictException.REASON_KEY_REUSED);

        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void anUnreproducibleOutcomeIsNeverRepeated() {
        OrderEntity winner = storedOrder(OrderStatus.FAILED);
        winner.fail(null, null);
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.of(winner));

        assertThatThrownBy(() -> service.checkout(KEY, request()))
                .isInstanceOf(CheckoutStateUnavailableException.class);

        verify(checkoutService, never()).performCheckout(any(), any());
    }

    @Test
    void theOwnerOfTheKeyRunsTheBusinessPartExactlyOnce() {
        OrderEntity claimed = storedOrder(OrderStatus.PENDING);
        when(orders.findByIdempotencyKeyHash(anyString())).thenReturn(Optional.empty());
        when(checkoutService.createPendingOrder(any(), any(), anyString(), anyString()))
                .thenReturn(claimed);
        when(checkoutService.performCheckout(any(), any()))
                .thenReturn(
                        OrderResponse.confirmed(
                                claimed.getId(),
                                claimed.getReservationId(),
                                claimed.getAuthorizationId(),
                                "SKU-1001",
                                1,
                                new BigDecimal("1499.00")));

        OrderResponse response = service.checkout(KEY, request());

        assertThat(response.orderId()).isEqualTo(claimed.getId());
        verify(checkoutService)
                .createPendingOrder(
                        any(), any(), eq(IdempotencyFingerprint.keyHash(KEY)), anyString());
        verify(checkoutService).performCheckout(eq(claimed), any());
    }
}
