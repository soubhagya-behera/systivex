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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The gateway enforces the same amount precision contract as order-service
 * (NUMERIC(12,2): 10 integer digits, 2 fractional digits) so a malformed
 * amount never leaves the edge.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GatewayAmountPrecisionTest {

    private static final String ORDER_SUCCESS =
            """
            {"orderId":"33333333-3333-3333-3333-333333333333",\
            "status":"CONFIRMED","reason":null,"message":"Checkout completed",\
            "reservationId":"11111111-1111-1111-1111-111111111111",\
            "authorizationId":"22222222-2222-2222-2222-222222222222",\
            "productId":"SKU-1001","quantity":1,"amount":1499.00}\
            """;

    private static HttpServer orderStub;
    private static int orderPort;
    private static final AtomicInteger received = new AtomicInteger();

    static {
        try {
            orderStub = HttpServer.create(new InetSocketAddress(0), 0);
            orderStub.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        received.incrementAndGet();
                        byte[] response = ORDER_SUCCESS.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(201, response.length);
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
        received.set(0);
    }

    @Autowired private TestRestTemplate rest;

    private ResponseEntity<String> post(BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "a-key");
        return rest.exchange(
                "/api/v1/checkout",
                HttpMethod.POST,
                new HttpEntity<>(
                        new CheckoutRequest("SKU-1001", 1, "CUST-1001", amount), headers),
                String.class);
    }

    @Test
    void excessiveFractionalPrecisionIsRejectedBeforeForwarding() {
        ResponseEntity<String> response = post(new BigDecimal("6000." + "9".repeat(513)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(received.get()).isZero();
    }

    @Test
    void excessiveIntegerPrecisionIsRejectedBeforeForwarding() {
        ResponseEntity<String> response = post(new BigDecimal("12345678901.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(received.get()).isZero();
    }

    @Test
    void maximumAcceptedPrecisionIsForwarded() {
        ResponseEntity<String> response = post(new BigDecimal("9999999999.99"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(received.get()).isOne();
    }
}
