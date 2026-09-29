package com.soubhagya.systivex.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Explicit contract for payment authorization requests. */
public record AuthorizeRequest(
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
