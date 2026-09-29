package com.soubhagya.systivex.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.gateway.api.CheckoutRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Proves an unreachable order-service becomes a controlled 502. Port 9
 * (discard) refuses connections immediately, so no stub is needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.order-service.url=http://localhost:9")
@AutoConfigureTestRestTemplate
class GatewayOrderUnavailableTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void unreachableOrderServiceBecomes502() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/checkout",
                        new CheckoutRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("order service unavailable");
        assertThat(response.getBody()).doesNotContain("localhost:9");
    }
}
