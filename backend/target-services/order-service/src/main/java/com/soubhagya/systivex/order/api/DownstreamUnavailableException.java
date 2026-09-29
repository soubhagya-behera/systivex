package com.soubhagya.systivex.order.api;

import java.util.UUID;

/** A downstream service is unreachable or erroring (no successful checkout). */
public class DownstreamUnavailableException extends CheckoutFailureException {

    public DownstreamUnavailableException(UUID orderId, OrderRequest request, String message) {
        super(orderId, request, message);
    }
}
