package com.soubhagya.systivex.order.api;

import java.util.UUID;

/** A downstream service is unreachable or erroring (no successful checkout). */
public class DownstreamUnavailableException extends CheckoutFailureException {

    public static final String REASON = "DOWNSTREAM_UNAVAILABLE";

    public DownstreamUnavailableException(UUID orderId, OrderRequest request, String message) {
        super(orderId, request, message);
    }

    @Override
    public String reason() {
        return REASON;
    }
}
