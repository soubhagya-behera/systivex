package com.soubhagya.systivex.observation;

/**
 * The sync endpoint was reached from beyond the loopback interface. Mapped
 * to HTTP 403. The check uses the container-reported transport peer only —
 * never spoofable request headers.
 */
public class SyncForbiddenException extends RuntimeException {

    public SyncForbiddenException(String message) {
        super(message);
    }
}
