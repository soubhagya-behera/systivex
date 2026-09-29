package com.soubhagya.systivex.order.downstream;

import java.math.BigDecimal;

/** Order-service copy of the payment-service authorization contract. */
public record AuthorizeRequest(String customerId, BigDecimal amount) {}
