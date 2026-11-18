package com.soubhagya.systivex.order.api;

/**
 * The supplied Idempotency-Key cannot identify a checkout: it was reused
 * with a different payload, or the attempt it identifies is still running.
 * Both are conflicts, never a silent second checkout.
 */
public class IdempotencyConflictException extends RuntimeException {

    /** Same key, different request payload — the key may not be reused. */
    public static final String REASON_KEY_REUSED = "IDEMPOTENCY_KEY_CONFLICT";

    /** The attempt that owns the key is still in progress. */
    public static final String REASON_IN_PROGRESS = "IDEMPOTENCY_KEY_IN_PROGRESS";

    private final String reason;

    public IdempotencyConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
