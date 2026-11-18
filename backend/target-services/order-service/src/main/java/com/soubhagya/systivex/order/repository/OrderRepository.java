package com.soubhagya.systivex.order.repository;

import com.soubhagya.systivex.order.model.OrderEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * Looks up the single order that owns an idempotency key. The key is
     * stored as a SHA-256 digest, so the lookup never touches a clear-text
     * client key. Backed by the unique index on {@code idempotency_key_hash}:
     * at most one row can ever match.
     */
    Optional<OrderEntity> findByIdempotencyKeyHash(String idempotencyKeyHash);
}
