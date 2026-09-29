package com.soubhagya.systivex.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Explicit contract for payment authorization outcomes. */
public record AuthorizeResponse(
        UUID authorizationId,
        String customerId,
        BigDecimal amount,
        String status) {}
