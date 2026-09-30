package com.soubhagya.systivex.order.model;

/**
 * Intentionally small order lifecycle for Phase 2B. An order starts
 * PENDING, becomes CONFIRMED only when both downstream calls succeeded,
 * and becomes FAILED when any downstream step fails. There is no
 * distributed transaction behind this — see CheckoutService.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
