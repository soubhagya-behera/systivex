package com.soubhagya.systivex.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment-service owned persistence: one row per authorization attempt.
 * orderReference is the calling order's id when the caller supplies one
 * (order-service does); it is informational only — there is deliberately no
 * foreign key to any order table, which lives in another service's database.
 */
@Entity
@Table(name = "payments")
public class PaymentTransaction {

    @Id private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "order_reference", length = 64)
    private String orderReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(
            UUID id, String customerId, String orderReference, BigDecimal amount, PaymentStatus status) {
        this.id = id;
        this.customerId = customerId;
        this.orderReference = orderReference;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
