package com.soubhagya.systivex.order.api;

/**
 * The Idempotency-Key header is syntactically unusable. The message never
 * echoes the supplied value: the key is never logged, never persisted in
 * clear text, and never leaves the service.
 */
public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
