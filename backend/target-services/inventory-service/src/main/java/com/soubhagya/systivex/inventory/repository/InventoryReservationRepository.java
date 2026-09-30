package com.soubhagya.systivex.inventory.repository;

import com.soubhagya.systivex.inventory.model.InventoryReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {}
