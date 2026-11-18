package com.soubhagya.systivex.order.api;

import java.util.UUID;

/** Base for checkout failures; carries enough context to build a FAILED body. */
public abstract class CheckoutFailureException extends RuntimeException {

    private final UUID orderId;
    private final OrderRequest request;

    protected CheckoutFailureException(UUID orderId, OrderRequest request, String message) {
        super(message);
        this.orderId = orderId;
        this.request = request;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderRequest getRequest() {
        return request;
    }

    /**
     * Stable machine readable classification of the failure. It is the value
     * the client sees as {@code reason} and the value persisted with the order
     * so a repeated request can reproduce the original outcome instead of
     * repeating the business operation.
     */
    public abstract String reason();
}
