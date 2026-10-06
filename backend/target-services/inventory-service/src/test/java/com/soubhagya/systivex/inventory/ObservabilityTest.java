package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.inventory.api.ReserveRequest;
import com.soubhagya.systivex.inventory.model.InventoryItem;
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
 * Observability slice: Prometheus exposition plus reservation counters for
 * both outcomes. Stock is reset so the test is order-independent. No
 * collector needed; only metric-family presence is asserted.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class ObservabilityTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Autowired private InventoryItemRepository items;

    @Autowired private InventoryReservationRepository reservations;

    @BeforeEach
    void resetAndExerciseBothOutcomes() {
        reservations.deleteAll();
        items.deleteAll();
        items.save(new InventoryItem("SKU-1001", 10));
        rest.postForEntity(
                "/internal/v1/inventory/reserve", new ReserveRequest("SKU-1001", 1), String.class);
        rest.postForEntity(
                "/internal/v1/inventory/reserve", new ReserveRequest("SKU-1001", 99), String.class);
    }

    @Test
    void prometheusExposesHttpAndReservationMetrics() {
        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_count");
        assertThat(metrics.getBody()).contains("inventory_reservation_success_total");
        assertThat(metrics.getBody()).contains("inventory_reservation_rejected_total");
    }
}
