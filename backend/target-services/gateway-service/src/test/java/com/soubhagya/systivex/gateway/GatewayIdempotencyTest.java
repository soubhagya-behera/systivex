package com.soubhagya.systivex.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.gateway.api.CheckoutRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The edge forwards the optional Idempotency-Key unchanged and stays a pure
 * forwarder: the conflict answers that come back from order-service pass
 * through untouched, and a request without the key produces a request without
 * the key downstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GatewayIdempotencyTest {

    private static final String KEY = "8f3c1d40-6a4e-4f0b-9d2e-1a2b3c4d5e6f";

    private static final String ORDER_SUCCESS =
            """
            {"orderId":"33333333-3333-3333-3333-333333333333",\
            "status":"CONFIRMED","reason":null,"message":"Checkout completed",\
            "reservationId":"11111111-1111-1111-1111-111111111111",\
            "authorizationId":"22222222-2222-2222-2222-222222222222",\
            "productId":"SKU-1001","quantity":1,"amount":1499.00}\
            """;

    private static final String ORDER_CONFLICT =
            """
            {"status":409,"error":"Conflict",\
            "message":"Idempotency-Key was already used with a different request payload",\
            "path":"/api/v1/orders","reason":"IDEMPOTENCY_KEY_CONFLICT"}\
            """;

    private static HttpServer orderStub;
    private static int orderPort;
    private static final AtomicInteger orderStatus = new AtomicInteger(201);
    private static final AtomicReference<String> orderBody =
            new AtomicReference<>(ORDER_SUCCESS);
    private static final List<String> receivedKeys = new CopyOnWriteArrayList<>();

    static {
        try {
            orderStub = HttpServer.create(new InetSocketAddress(0), 0);
            orderStub.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        receivedKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
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
        receivedKeys.clear();
    }

    @Autowired private TestRestTemplate rest;

    private CheckoutRequest checkoutRequest() {
        return new CheckoutRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    private ResponseEntity<String> post(String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange(
                "/api/v1/checkout",
                HttpMethod.POST,
                new HttpEntity<>(checkoutRequest(), headers),
                String.class);
    }

    @Test
    void forwardsTheIdempotencyKeyUnchanged() {
        ResponseEntity<String> response = post(KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(receivedKeys).containsExactly(KEY);
    }

    @Test
    void omitsTheHeaderWhenTheClientSendsNone() {
        ResponseEntity<String> response = post(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(receivedKeys).hasSize(1);
        assertThat(receivedKeys.get(0)).isNull();
    }

    @Test
    void forwardsAnEmptyKeyUnchanged() {
        // The edge does not decide what a key means; order-service rejects
        // what it cannot use. Silently dropping the header would turn a
        // client mistake into an unguarded checkout.
        ResponseEntity<String> response = post("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(receivedKeys).hasSize(1);
        assertThat(receivedKeys.get(0)).isEmpty();
    }

    @Test
    void passesTheConflictStatusAndBodyThrough() {
        orderStatus.set(409);
        orderBody.set(ORDER_CONFLICT);

        ResponseEntity<String> response = post(KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(response.getBody()).contains("409");
        assertThat(receivedKeys).containsExactly(KEY);
    }
}
