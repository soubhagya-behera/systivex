package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.inventory.api.ReserveRequest;
import com.soubhagya.systivex.inventory.api.ReserveResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InventoryServiceTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void reservesWithinDemoLimit() {
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
    }

    @Test
    void rejectsAboveDemoLimitWith422() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/inventory/reserve",
                        new ReserveRequest("SKU-1001", 6),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("Insufficient stock");
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
