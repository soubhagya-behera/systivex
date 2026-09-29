package com.soubhagya.systivex.order.api;

import java.util.UUID;

/** Payment declined the authorization (business failure, not a crash). */
public class PaymentDeclinedException extends CheckoutFailureException {

    public PaymentDeclinedException(UUID orderId, OrderRequest request, String message) {
        super(orderId, request, message);
    }
}
