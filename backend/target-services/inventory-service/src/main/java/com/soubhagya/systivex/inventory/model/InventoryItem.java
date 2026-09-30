package com.soubhagya.systivex.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Inventory-service owned stock row: one per product. Quantity only ever
 * moves inside a locked reserve transaction (see InventoryReservationService);
 * the version column gives optimistic-locking protection on top.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {}

    public InventoryItem(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void decrease(int quantity) {
        if (quantity > availableQuantity) {
            throw new IllegalArgumentException(
                    "Cannot reserve " + quantity + " of " + productId + ": only "
                            + availableQuantity + " available");
        }
        this.availableQuantity -= quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
