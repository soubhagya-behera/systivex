package com.soubhagya.systivex.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Explicit contract for payment authorization requests. orderReference is
 * optional: order-service sends its order id so the payment row can be traced
 * back to the checkout attempt. Absence stays valid for callers without an
 * order context.
 */
public record AuthorizeRequest(
        @NotBlank String customerId,
        String orderReference,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {}
