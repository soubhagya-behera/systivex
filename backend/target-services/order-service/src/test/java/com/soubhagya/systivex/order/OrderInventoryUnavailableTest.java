package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.model.OrderStatus;
import com.soubhagya.systivex.order.repository.OrderRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Proves an unreachable inventory service fails the checkout in a controlled
 * way, and that the failed attempt is still recorded. Port 9 (discard)
 * refuses connections immediately, so no stub is needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.inventory-service.url=http://localhost:9")
@AutoConfigureTestRestTemplate
class OrderInventoryUnavailableTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Autowired private OrderRepository orders;

    @Test
    void unreachableInventoryFailsCheckoutWith503() {
        ResponseEntity<OrderResponse> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("FAILED");
        assertThat(response.getBody().reason()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("localhost:9");

        OrderEntity persisted = orders.findById(response.getBody().orderId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.FAILED);
    }
}
