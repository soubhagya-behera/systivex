package com.soubhagya.systivex.order.model;

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
 * Order-service owned persistence. This row records the outcome of one
 * checkout attempt: downstream identifiers are filled in only on success.
 * Kept deliberately small — no state machine beyond PENDING/CONFIRMED/FAILED.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {}

    public OrderEntity(
            UUID id, String customerId, String productId, int quantity, BigDecimal amount) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void confirm(UUID reservationId, UUID authorizationId) {
        this.status = OrderStatus.CONFIRMED;
        this.reservationId = reservationId;
        this.authorizationId = authorizationId;
    }

    public void fail() {
        this.status = OrderStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getAuthorizationId() {
        return authorizationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
