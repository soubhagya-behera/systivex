package com.soubhagya.systivex.inventory.repository;

import com.soubhagya.systivex.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Row-level write lock for the reserve path: concurrent reservations for
     * the same product serialize here instead of losing updates to each other.
     * Combined with the @Version column, this keeps the stock count honest
     * without Redis or any external coordinator.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findLockedByProductId(String productId);
}
