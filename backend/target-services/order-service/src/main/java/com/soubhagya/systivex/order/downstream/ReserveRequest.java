package com.soubhagya.systivex.order.downstream;

/** Order-service copy of the inventory-service reserve contract. */
public record ReserveRequest(String productId, int quantity) {}
