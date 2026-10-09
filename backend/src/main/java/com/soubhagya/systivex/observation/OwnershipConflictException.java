package com.soubhagya.systivex.observation;

/**
 * A connector-owned identity is already taken by a record the connector
 * does not own. The sync aborts instead of silently claiming ownership.
 * Mapped to HTTP 409.
 */
public class OwnershipConflictException extends RuntimeException {

    public OwnershipConflictException(String message) {
        super(message);
    }
}
