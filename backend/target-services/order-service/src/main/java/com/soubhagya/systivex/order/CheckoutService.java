package com.soubhagya.systivex.order;

import com.soubhagya.systivex.order.api.DownstreamUnavailableException;
import com.soubhagya.systivex.order.api.InventoryRejectedException;
import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.api.PaymentDeclinedException;
import com.soubhagya.systivex.order.downstream.AuthorizeRequest;
import com.soubhagya.systivex.order.downstream.AuthorizeResponse;
import com.soubhagya.systivex.order.downstream.ReserveRequest;
import com.soubhagya.systivex.order.downstream.ReserveResponse;
import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.repository.OrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Phase 2B checkout orchestration with persistent orders.
 *
 * <p>Flow per attempt: validate (controller) → insert PENDING row → reserve
 * stock over HTTP → authorize payment over HTTP → update the row to CONFIRMED
 * only when both succeeded, FAILED otherwise. Each repository save is its own
 * local order-database transaction; the inventory and payment databases commit
 * in their own services. There is deliberately no distributed transaction, so
 * this method is NOT atomic: if payment fails after inventory succeeded, the
 * order is FAILED but the inventory reservation stands (no cross-service
 * rollback — the order service must never write another service's database).
 * A crash between the PENDING insert and the final update likewise leaves a
 * PENDING row behind. Both limitations are accepted for this phase and will
 * matter to a future reconciliation/simulation story.
 */
@Service
public class CheckoutService {

    private final RestClient inventoryClient;
    private final RestClient paymentClient;
    private final OrderRepository orders;

    public CheckoutService(
            RestClient inventoryClient, RestClient paymentClient, OrderRepository orders) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.orders = orders;
    }

    public OrderResponse checkout(UUID orderId, OrderRequest request) {
        OrderEntity order =
                orders.save(
                        new OrderEntity(
                                orderId,
                                request.customerId(),
                                request.productId(),
                                request.quantity(),
                                request.amount()));

        ReserveResponse reservation;
        try {
            reservation = reserve(orderId, request);
        } catch (InventoryRejectedException | DownstreamUnavailableException e) {
            order.fail();
            orders.save(order);
            throw e;
        }

        AuthorizeResponse authorization;
        try {
            authorization = authorize(orderId, request);
        } catch (PaymentDeclinedException | DownstreamUnavailableException e) {
            order.fail();
            orders.save(order);
            throw e;
        }

        order.confirm(reservation.reservationId(), authorization.authorizationId());
        orders.save(order);
        return OrderResponse.confirmed(
                orderId,
                reservation.reservationId(),
                authorization.authorizationId(),
                request.productId(),
                request.quantity(),
                request.amount());
    }

    private ReserveResponse reserve(UUID orderId, OrderRequest request) {
        try {
            return inventoryClient
                    .post()
                    .uri("/internal/v1/inventory/reserve")
                    .body(new ReserveRequest(request.productId(), request.quantity()))
                    .retrieve()
                    .body(ReserveResponse.class);
        } catch (HttpClientErrorException e) {
            throw new InventoryRejectedException(
                    orderId,
                    request,
                    "Inventory reservation rejected for product " + request.productId());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new DownstreamUnavailableException(
                    orderId, request, "Inventory service unavailable; checkout cannot complete");
        }
    }

    private AuthorizeResponse authorize(UUID orderId, OrderRequest request) {
        try {
            return paymentClient
                    .post()
                    .uri("/internal/v1/payments/authorize")
                    .body(
                            new AuthorizeRequest(
                                    request.customerId(),
                                    orderId.toString(),
                                    request.amount()))
                    .retrieve()
                    .body(AuthorizeResponse.class);
        } catch (HttpClientErrorException e) {
            throw new PaymentDeclinedException(
                    orderId,
                    request,
                    "Payment authorization declined for customer " + request.customerId());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new DownstreamUnavailableException(
                    orderId, request, "Payment service unavailable; checkout cannot complete");
        }
    }
}
