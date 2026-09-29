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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Phase 2A checkout orchestration: reserve stock, then authorize payment.
 * Any downstream business rejection or outage fails the checkout — a
 * successful response is only ever returned when both calls succeeded.
 * Stateless (no database yet; persistence arrives in Phase 2B).
 */
@Service
public class CheckoutService {

    private final RestClient inventoryClient;
    private final RestClient paymentClient;

    public CheckoutService(RestClient inventoryClient, RestClient paymentClient) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public OrderResponse checkout(UUID orderId, OrderRequest request) {
        ReserveResponse reservation = reserve(orderId, request);
        AuthorizeResponse authorization = authorize(orderId, request);
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
                    .body(new AuthorizeRequest(request.customerId(), request.amount()))
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
