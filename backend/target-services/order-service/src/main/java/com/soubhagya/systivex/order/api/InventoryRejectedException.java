package com.soubhagya.systivex.order.api;

import java.util.UUID;

/** Inventory rejected the reservation (business failure, not a crash). */
public class InventoryRejectedException extends CheckoutFailureException {

    public static final String REASON = "INVENTORY_REJECTED";

    public InventoryRejectedException(UUID orderId, OrderRequest request, String message) {
        super(orderId, request, message);
    }

    @Override
    public String reason() {
        return REASON;
    }
}
