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
 *
 * <p>The same row also carries the optional idempotency linkage: a client
 * supplied key is stored only as a SHA-256 digest, never in clear text. One
 * key identifies exactly one order row, enforced by the unique index on
 * {@code idempotency_key_hash}; a null digest means the request carried no
 * key and behaves exactly as before this feature existed.
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

    /**
     * SHA-256 hex digest of the client supplied Idempotency-Key, or null when
     * the request carried no key. Never the raw key.
     */
    @Column(name = "idempotency_key_hash", length = 64)
    private String idempotencyKeyHash;

    /**
     * SHA-256 hex digest of the canonical request payload that was accepted
     * under the key. A later request with the same key but a different
     * payload is a conflict, not a replay.
     */
    @Column(name = "idempotency_request_hash", length = 64)
    private String idempotencyRequestHash;

    /** Machine readable failure classification, e.g. INVENTORY_REJECTED. */
    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    /** Sanitized failure message, kept so a failure can be replayed verbatim. */
    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {}

    public OrderEntity(
            UUID id, String customerId, String productId, int quantity, BigDecimal amount) {
        this(id, customerId, productId, quantity, amount, null, null);
    }

    public OrderEntity(
            UUID id,
            String customerId,
            String productId,
            int quantity,
            BigDecimal amount,
            String idempotencyKeyHash,
            String idempotencyRequestHash) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
        this.idempotencyKeyHash = idempotencyKeyHash;
        this.idempotencyRequestHash = idempotencyRequestHash;
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

    /**
     * Marks the attempt failed and records why, so a repeated request with
     * the same idempotency key can reproduce the original outcome instead of
     * repeating the business operation.
     */
    public void fail(String reason, String message) {
        fail();
        this.failureReason = reason;
        this.failureMessage = message;
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

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public String getIdempotencyRequestHash() {
        return idempotencyRequestHash;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
