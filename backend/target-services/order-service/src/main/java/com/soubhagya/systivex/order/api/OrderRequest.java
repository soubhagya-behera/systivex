package com.soubhagya.systivex.order.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Explicit contract for checkout order requests. */
public record OrderRequest(
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
