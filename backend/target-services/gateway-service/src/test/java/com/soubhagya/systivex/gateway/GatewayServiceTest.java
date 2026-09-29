package com.soubhagya.systivex.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.gateway.api.CheckoutRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gateway boundary tests over real HTTP against a stub order-service.
 * Nothing here needs the real order process running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GatewayServiceTest {

    private static final String ORDER_SUCCESS =
            """
            {"orderId":"33333333-3333-3333-3333-333333333333",\
            "status":"CONFIRMED","reason":null,"message":"Checkout completed",\
            "reservationId":"11111111-1111-1111-1111-111111111111",\
            "authorizationId":"22222222-2222-2222-2222-222222222222",\
            "productId":"SKU-1001","quantity":1,"amount":1499.00}\
            """;
    private static final String ORDER_REJECTED =
            """
            {"orderId":"33333333-3333-3333-3333-333333333333",\
            "status":"FAILED","reason":"INVENTORY_REJECTED",\
            "message":"Inventory reservation rejected",\
            "reservationId":null,"authorizationId":null,\
            "productId":"SKU-1001","quantity":99,"amount":1499.00}\
            """;

    private static HttpServer orderStub;
    private static int orderPort;
    private static final AtomicInteger orderStatus = new AtomicInteger(201);
    private static final AtomicReference<String> orderBody =
            new AtomicReference<>(ORDER_SUCCESS);

    static {
        try {
            orderStub = HttpServer.create(new InetSocketAddress(0), 0);
            orderStub.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        byte[] response = orderBody.get().getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(orderStatus.get(), response.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(response);
                        }
                    });
            orderStub.setExecutor(Executors.newCachedThreadPool());
            orderStub.start();
            orderPort = orderStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub order-service", e);
        }
    }

    @DynamicPropertySource
    static void orderUrl(DynamicPropertyRegistry registry) {
        registry.add("app.order-service.url", () -> "http://localhost:" + orderPort);
    }

    @AfterAll
    static void stopStub() {
        orderStub.stop(0);
    }

    @BeforeEach
    void resetStub() {
        orderStatus.set(201);
        orderBody.set(ORDER_SUCCESS);
    }

    @Autowired private TestRestTemplate rest;

    private CheckoutRequest checkoutRequest() {
        return new CheckoutRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    @Test
    void forwardsSuccessfulCheckout() {
        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/checkout", checkoutRequest(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"CONFIRMED\"");
        assertThat(response.getBody()).contains("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void forwardsDownstreamRejectionWithStatus() {
        orderStatus.set(422);
        orderBody.set(ORDER_REJECTED);

        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/checkout", checkoutRequest(), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("INVENTORY_REJECTED");
    }

    @Test
    void downstreamErrorBecomesGeneric502() {
        orderStatus.set(500);
        orderBody.set("{\"exception\":\"NullPointerException\",\"stack\":\"at ...\"}");

        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/checkout", checkoutRequest(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("order service unavailable");
        assertThat(response.getBody()).doesNotContain("NullPointerException");
    }

    @Test
    void invalidCheckoutFailsWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/checkout",
                        new CheckoutRequest("", 0, "", null),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
