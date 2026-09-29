package com.soubhagya.systivex.payment.api;

/** Business rejection (not a validation error): the amount is declined. */
public class AuthorizationDeclinedException extends RuntimeException {

    public AuthorizationDeclinedException(String message) {
        super(message);
    }
}
