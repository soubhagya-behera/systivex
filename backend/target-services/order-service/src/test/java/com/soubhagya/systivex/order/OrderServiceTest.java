package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
 * Order-service boundary tests over real HTTP. Downstream services are JDK
 * stub servers (no extra test dependencies); nothing here needs the real
 * inventory/payment processes running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderServiceTest {

    private static final String INVENTORY_SUCCESS =
            """
            {"reservationId":"11111111-1111-1111-1111-111111111111",\
            "productId":"SKU-1001","quantity":1,"status":"RESERVED"}\
            """;
    private static final String PAYMENT_SUCCESS =
            """
            {"authorizationId":"22222222-2222-2222-2222-222222222222",\
            "customerId":"CUST-1001","amount":1499.00,"status":"AUTHORIZED"}\
            """;
    private static final String BUSINESS_FAILURE =
            """
            {"status":422,"error":"Unprocessable Entity","message":"demo rejection"}\
            """;

    private static HttpServer inventoryStub;
    private static HttpServer paymentStub;
    private static int inventoryPort;
    private static int paymentPort;
    private static final AtomicInteger inventoryStatus = new AtomicInteger(201);
    private static final AtomicReference<String> inventoryBody =
            new AtomicReference<>(INVENTORY_SUCCESS);
    private static final AtomicInteger paymentStatus = new AtomicInteger(201);
    private static final AtomicReference<String> paymentBody =
            new AtomicReference<>(PAYMENT_SUCCESS);

    static {
        try {
            inventoryStub = stub(inventoryStatus, inventoryBody);
            paymentStub = stub(paymentStatus, paymentBody);
            inventoryPort = inventoryStub.getAddress().getPort();
            paymentPort = paymentStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub servers", e);
        }
    }

    private static HttpServer stub(AtomicInteger status, AtomicReference<String> body)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status.get(), response.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(response);
                    }
                });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("app.inventory-service.url", () -> "http://localhost:" + inventoryPort);
        registry.add("app.payment-service.url", () -> "http://localhost:" + paymentPort);
    }

    @AfterAll
    static void stopStubs() {
        inventoryStub.stop(0);
        paymentStub.stop(0);
    }

    @BeforeEach
    void resetStubs() {
        inventoryStatus.set(201);
        inventoryBody.set(INVENTORY_SUCCESS);
        paymentStatus.set(201);
        paymentBody.set(PAYMENT_SUCCESS);
    }

    @Autowired private TestRestTemplate rest;

    private OrderRequest checkoutRequest() {
        return new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    @Test
    void successfulCheckout() {
        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/v1/orders", checkoutRequest(), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().reservationId()).isNotNull();
        assertThat(response.getBody().authorizationId()).isNotNull();
    }

    @Test
    void inventoryRejectionFailsCheckoutWith422() {
        inventoryStatus.set(422);
        inventoryBody.set(BUSINESS_FAILURE);

        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/v1/orders", checkoutRequest(), OrderResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("FAILED");
        assertThat(response.getBody().reason()).isEqualTo("INVENTORY_REJECTED");
        assertThat(response.getBody().orderId()).isNotNull();
    }

    @Test
    void paymentDeclineFailsCheckoutWith422() {
        paymentStatus.set(422);
        paymentBody.set(BUSINESS_FAILURE);

        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/v1/orders", checkoutRequest(), OrderResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("FAILED");
        assertThat(response.getBody().reason()).isEqualTo("PAYMENT_DECLINED");
    }

    @Test
    void downstreamErrorFailsCheckoutWith503() {
        paymentStatus.set(500);
        paymentBody.set("{}");

        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/v1/orders", checkoutRequest(), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("FAILED");
        assertThat(response.getBody().reason()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
    }

    @Test
    void malformedRequestFailsWith400() {
        ResponseEntity<Map> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("", 0, "", null),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
