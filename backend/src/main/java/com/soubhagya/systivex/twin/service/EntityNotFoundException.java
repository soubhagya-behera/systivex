package com.soubhagya.systivex.twin.service;

/** Thrown when a requested twin record does not exist. Mapped to 404. */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
