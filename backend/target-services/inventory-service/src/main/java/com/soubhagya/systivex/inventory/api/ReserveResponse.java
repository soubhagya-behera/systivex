package com.soubhagya.systivex.inventory.api;

import java.util.UUID;

/** Explicit contract for stock reservation outcomes. */
public record ReserveResponse(
        UUID reservationId,
        String productId,
        int quantity,
        String status) {}
