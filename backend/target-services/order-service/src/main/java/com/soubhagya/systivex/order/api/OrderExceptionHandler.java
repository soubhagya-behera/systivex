package com.soubhagya.systivex.order.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controlled checkout failures: business rejections become 422 with a
 * traceable FAILED body, outages become 503, idempotency conflicts become
 * 409. No stack traces, no downstream URLs, no exception names on the wire.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "message", "Request validation failed",
                "path", request.getRequestURI(),
                "fieldErrors", fieldErrors);
    }

    /**
     * The Idempotency-Key cannot identify a checkout: either it was reused
     * with a different payload, or the attempt it identifies is still
     * running. Never a second order, never a repeated downstream call.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public IdempotencyConflictResponse handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest request) {
        return new IdempotencyConflictResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.getReason());
    }

    /** The supplied Idempotency-Key is unusable. The value is never echoed. */
    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException ex, HttpServletRequest request) {
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "message", ex.getMessage(),
                "path", request.getRequestURI());
    }

    /** A recorded outcome could not be reproduced; nothing is repeated. */
    @ExceptionHandler(CheckoutStateUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleStateUnavailable(
            CheckoutStateUnavailableException ex, HttpServletRequest request) {
        return Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "message", ex.getMessage(),
                "path", request.getRequestURI());
    }

    @ExceptionHandler(InventoryRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public OrderResponse handleInventoryRejected(InventoryRejectedException ex) {
        return OrderResponse.failed(
                ex.getOrderId(), ex.reason(), ex.getMessage(), ex.getRequest());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public OrderResponse handlePaymentDeclined(PaymentDeclinedException ex) {
        return OrderResponse.failed(ex.getOrderId(), ex.reason(), ex.getMessage(), ex.getRequest());
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public OrderResponse handleUnavailable(DownstreamUnavailableException ex) {
        return OrderResponse.failed(ex.getOrderId(), ex.reason(), ex.getMessage(), ex.getRequest());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleDatabaseFailure(
            DataAccessException ex, HttpServletRequest request) {
        // Order storage is unavailable. No SQL text, exception names, or
        // connection details leave the service — just a controlled 503.
        return Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "message", "Order storage unavailable; checkout cannot complete",
                "path", request.getRequestURI());
    }
}
