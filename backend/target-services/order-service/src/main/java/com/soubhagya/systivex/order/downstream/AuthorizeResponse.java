package com.soubhagya.systivex.order.downstream;

import java.math.BigDecimal;
import java.util.UUID;

/** Order-service copy of the payment-service authorization response. */
public record AuthorizeResponse(
        UUID authorizationId, String customerId, BigDecimal amount, String status) {}
