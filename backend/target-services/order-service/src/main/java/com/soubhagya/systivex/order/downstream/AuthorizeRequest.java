package com.soubhagya.systivex.order.downstream;

import java.math.BigDecimal;

/**
 * Order-service copy of the payment-service authorization contract.
 * orderReference carries our order id so the payment row can be traced back
 * to the checkout attempt; it is optional and stays null for callers that
 * do not have an order context.
 */
public record AuthorizeRequest(String customerId, String orderReference, BigDecimal amount) {}
