package com.soubhagya.systivex.order.downstream;

import java.util.UUID;

/** Order-service copy of the inventory-service reserve response. */
public record ReserveResponse(
        UUID reservationId, String productId, int quantity, String status) {}
