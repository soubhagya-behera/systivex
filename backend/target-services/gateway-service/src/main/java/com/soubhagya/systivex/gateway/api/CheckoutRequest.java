package com.soubhagya.systivex.gateway.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Explicit edge contract for checkout. Validated here, forwarded as-is. */
public record CheckoutRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
