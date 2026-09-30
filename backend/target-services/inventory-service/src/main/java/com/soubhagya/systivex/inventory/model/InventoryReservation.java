package com.soubhagya.systivex.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Inventory-service owned persistence: one row per reservation attempt,
 * including rejections. productId is informational only — there is
 * deliberately no foreign key to inventory_items so that attempts for
 * unknown products can still be recorded.
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id private UUID id;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryReservation() {}

    public InventoryReservation(UUID id, String productId, int quantity, ReservationStatus status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
