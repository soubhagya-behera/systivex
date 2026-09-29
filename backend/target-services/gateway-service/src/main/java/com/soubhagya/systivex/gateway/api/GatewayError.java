package com.soubhagya.systivex.gateway.api;

/** Gateway error body for failures the gateway itself produces (502s). */
public record GatewayError(int status, String error, String message) {}
