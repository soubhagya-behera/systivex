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
 * traceable FAILED body, outages become 503. No stack traces, no downstream
 * URLs, no exception names on the wire.
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

    @ExceptionHandler(InventoryRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public OrderResponse handleInventoryRejected(InventoryRejectedException ex) {
        return OrderResponse.failed(
                ex.getOrderId(), "INVENTORY_REJECTED", ex.getMessage(), ex.getRequest());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public OrderResponse handlePaymentDeclined(PaymentDeclinedException ex) {
        return OrderResponse.failed(
                ex.getOrderId(), "PAYMENT_DECLINED", ex.getMessage(), ex.getRequest());
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public OrderResponse handleUnavailable(DownstreamUnavailableException ex) {
        return OrderResponse.failed(
                ex.getOrderId(), "DOWNSTREAM_UNAVAILABLE", ex.getMessage(), ex.getRequest());
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
