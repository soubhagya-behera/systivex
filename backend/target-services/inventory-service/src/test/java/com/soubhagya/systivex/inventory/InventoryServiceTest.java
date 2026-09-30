package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.inventory.api.ReserveRequest;
import com.soubhagya.systivex.inventory.api.ReserveResponse;
import com.soubhagya.systivex.inventory.model.InventoryItem;
import com.soubhagya.systivex.inventory.model.ReservationStatus;
import com.soubhagya.systivex.inventory.repository.InventoryItemRepository;
import com.soubhagya.systivex.inventory.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Reservation boundary tests over real HTTP against a throwaway PostgreSQL
 * (Flyway-migrated + seeded). Stock is reset before every test so tests stay
 * independent of execution order. Nothing here touches the developer's local
 * inventory_db.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InventoryServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Autowired private InventoryItemRepository items;

    @Autowired private InventoryReservationRepository reservations;

    @BeforeEach
    void resetStock() {
        reservations.deleteAll();
        items.deleteAll();
        items.save(new InventoryItem("SKU-1001", 10));
    }

    @Test
    void reservesWithinStockAndDecreasesQuantity() {
        ResponseEntity<ReserveResponse> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("SKU-1001", 2),
                        ReserveResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reservationId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("RESERVED");
        assertThat(response.getBody().quantity()).isEqualTo(2);

        assertThat(items.findById("SKU-1001").orElseThrow().getAvailableQuantity())
                .isEqualTo(8);
        assertThat(
                        reservations
                                .findById(response.getBody().reservationId())
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void rejectsAboveStockWith422AndKeepsQuantity() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("SKU-1001", 99),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("Insufficient stock");

        // Failed attempt changes nothing about the stock — and is recorded.
        assertThat(items.findById("SKU-1001").orElseThrow().getAvailableQuantity())
                .isEqualTo(10);
        assertThat(reservations.findAll())
                .hasSize(1)
                .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.REJECTED));
    }

    @Test
    void rejectsUnknownProductWith422() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("SKU-NOPE", 1),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("Unknown product");
    }

    @Test
    void rejectsZeroQuantityWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("SKU-1001", 0),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsBlankProductWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("  ", 1),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response =
                rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
