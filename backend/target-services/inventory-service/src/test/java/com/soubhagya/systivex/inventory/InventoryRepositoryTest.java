package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.inventory.model.InventoryItem;
import com.soubhagya.systivex.inventory.model.InventoryReservation;
import com.soubhagya.systivex.inventory.model.ReservationStatus;
import com.soubhagya.systivex.inventory.repository.InventoryItemRepository;
import com.soubhagya.systivex.inventory.repository.InventoryReservationRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InventoryRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired private InventoryItemRepository items;

    @Autowired private InventoryReservationRepository reservations;

    @Autowired private EntityManager entityManager;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void persistsItemAndFindsIt() {
        items.saveAndFlush(new InventoryItem("SKU-9001", 7));

        entityManager.clear();

        InventoryItem found = items.findById("SKU-9001").orElseThrow();
        assertThat(found.getAvailableQuantity()).isEqualTo(7);
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistsReservationWithoutItemForeignKey() {
        // Rejected attempts for unknown products are still recorded — by
        // design there is no FK between reservations and items.
        InventoryReservation saved =
                reservations.saveAndFlush(
                        new InventoryReservation(
                                UUID.randomUUID(), "SKU-UNKNOWN", 3, ReservationStatus.REJECTED));

        entityManager.clear();

        InventoryReservation found = reservations.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsNegativeStock() {
        InventoryItem item = new InventoryItem("SKU-9002", 1);
        items.saveAndFlush(item);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE inventory_items SET available_quantity = -1 WHERE"
                                                + " product_id = 'SKU-9002'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownReservationStatus() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO inventory_reservations (id, product_id,"
                                                + " quantity, status) VALUES (?, 'SKU-9001', 1,"
                                                + " 'PENDING')",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
