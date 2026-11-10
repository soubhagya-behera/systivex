package com.soubhagya.systivex.localmodel;

public final class OperationDeadlineExceededException extends RuntimeException {

    public OperationDeadlineExceededException(String message) {
        super(message);
    }

    public OperationDeadlineExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
