package com.soubhagya.systivex.inventory.api;

/** Small stable error body; never carries stack traces or internals. */
public record ErrorResponse(int status, String error, String message) {}
