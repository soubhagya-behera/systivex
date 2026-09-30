package com.soubhagya.systivex.payment.model;

/** Authorization outcome. Both outcomes are persisted — a decline is a recorded result, not a non-event. */
public enum PaymentStatus {
    AUTHORIZED,
    DECLINED
}
