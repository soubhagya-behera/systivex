package com.soubhagya.systivex.inventory.api;

/** Business rejection (not a validation error): stock cannot cover the request. */
public class ReservationRejectedException extends RuntimeException {

    public ReservationRejectedException(String message) {
        super(message);
    }
}
