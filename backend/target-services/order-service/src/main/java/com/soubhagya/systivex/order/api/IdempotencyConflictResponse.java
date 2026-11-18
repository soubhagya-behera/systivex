package com.soubhagya.systivex.order.api;

/**
 * Body of an idempotency conflict (HTTP 409). It follows the generic error
 * shape the service already uses for controlled failures and adds one
 * machine-readable {@code reason} so a client can tell the two conflict
 * cases apart without parsing prose.
 */
public record IdempotencyConflictResponse(
        int status, String error, String message, String path, String reason) {}
