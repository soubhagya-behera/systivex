package com.soubhagya.systivex.order.api;

/**
 * The stored outcome of an idempotent checkout could not be reproduced. The
 * attempt itself is never repeated in this state: repeating it could charge
 * the customer twice.
 */
public class CheckoutStateUnavailableException extends RuntimeException {

    public CheckoutStateUnavailableException(String message) {
        super(message);
    }
}
