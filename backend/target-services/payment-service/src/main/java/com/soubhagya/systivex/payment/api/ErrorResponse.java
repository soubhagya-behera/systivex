package com.soubhagya.systivex.payment.api;

/** Small stable error body; never carries stack traces or internals. */
public record ErrorResponse(int status, String error, String message) {}
