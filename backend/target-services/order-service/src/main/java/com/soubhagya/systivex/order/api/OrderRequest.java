package com.soubhagya.systivex.order.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Explicit contract for checkout order requests.
 *
 * <p>The amount must fit the order schema's NUMERIC(12,2): 10 integer digits
 * and 2 fractional digits. Enforcing that here (and at the gateway) prevents
 * a value the database would silently round from ever reaching inventory or
 * payment, where it would be charged at a different precision than the order
 * records.
 */
public record OrderRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotBlank String customerId,
        @NotNull
                @DecimalMin(value = "0.01")
                @Digits(integer = 10, fraction = 2)
                BigDecimal amount) {}
