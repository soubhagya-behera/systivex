package com.soubhagya.systivex.order.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Explicit contract for checkout outcomes. Failed checkouts still carry the
 * generated order id so the attempt can be traced in logs.
 */
public record OrderResponse(
        UUID orderId,
        String status,
        String reason,
        String message,
        UUID reservationId,
        UUID authorizationId,
        String productId,
        int quantity,
        BigDecimal amount) {

    public static OrderResponse confirmed(
            UUID orderId,
            UUID reservationId,
            UUID authorizationId,
            String productId,
            int quantity,
            BigDecimal amount) {
        return new OrderResponse(
                orderId,
                "CONFIRMED",
                null,
                "Checkout completed",
                reservationId,
                authorizationId,
                productId,
                quantity,
                amount);
    }

    public static OrderResponse failed(
            UUID orderId, String reason, String message, OrderRequest request) {
        return new OrderResponse(
                orderId,
                "FAILED",
                reason,
                message,
                null,
                null,
                request.productId(),
                request.quantity(),
                request.amount());
    }
}
