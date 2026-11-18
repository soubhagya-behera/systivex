package com.soubhagya.systivex.gateway.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Explicit edge contract for checkout. Validated here, forwarded as-is.
 *
 * <p>The amount must fit the order schema's NUMERIC(12,2): 10 integer digits
 * and 2 fractional digits. The same bound is enforced by order-service; the
 * edge rejects first so a malformed amount is never forwarded.
 */
public record CheckoutRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotBlank String customerId,
        @NotNull
                @DecimalMin(value = "0.01")
                @Digits(integer = 10, fraction = 2)
                BigDecimal amount) {}
