package com.soubhagya.systivex.order.repository;

import com.soubhagya.systivex.order.model.OrderEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {}
