package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.repository.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The accepted amount precision is the precision of the order schema,
 * NUMERIC(12,2): 10 integer digits and 2 fractional digits. Values outside
 * that contract must be refused at the validation boundary — before the
 * order claim, before inventory, and before payment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AmountPrecisionTest extends AbstractPostgresIntegrationTest {

    private static final String KEY = "amount-precision-key";
    private static final String INVENTORY_SUCCESS =
            """
            {"reservationId":"11111111-1111-1111-1111-111111111111",\
            "productId":"SKU-1001","quantity":1,"status":"RESERVED"}\
            """;
    private static final String PAYMENT_SUCCESS =
            """
            {"authorizationId":"22222222-2222-2222-2222-222222222222",\
            "customerId":"CUST-1001","amount":5000.00,"status":"AUTHORIZED"}\
            """;
    private static final String PAYMENT_DECLINED =
            """
            {"status":422,"error":"Unprocessable Entity","message":"demo decline"}\
            """;

    private static HttpServer inventoryStub;
    private static HttpServer paymentStub;
    private static int inventoryPort;
    private static int paymentPort;
    private static final AtomicInteger inventoryCalls = new AtomicInteger();
    private static final AtomicInteger paymentCalls = new AtomicInteger();

    static {
        try {
            inventoryStub = stub(INVENTORY_SUCCESS, inventoryCalls);
            paymentStub = stub(PAYMENT_SUCCESS, paymentCalls);
            inventoryPort = inventoryStub.getAddress().getPort();
            paymentPort = paymentStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub servers", e);
        }
    }

    private static HttpServer stub(String body, AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    calls.incrementAndGet();
                    byte[] response = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, response.length);
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
    void resetState() {
        inventoryCalls.set(0);
        paymentCalls.set(0);
        orders.deleteAll();
    }

    @Autowired private TestRestTemplate rest;

    @Autowired private OrderRepository orders;

    @Autowired private JdbcTemplate jdbc;

    private ResponseEntity<String> post(String key, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(new OrderRequest("SKU-1001", 1, "CUST-1001", amount), headers),
                String.class);
    }

    private void assertNothingHappened() {
        assertThat(orders.count()).isZero();
        assertThat(inventoryCalls.get()).isZero();
        assertThat(paymentCalls.get()).isZero();
        Long keyedRows =
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE idempotency_key_hash IS NOT NULL",
                        Long.class);
        assertThat(keyedRows).isZero();
    }

    @Test
    void excessiveFractionalPrecisionIsRejectedBeforeAnyOperation() {
        // NUMERIC(12,2) stores two fractional digits; 513 nines cannot be
        // represented and must never reach the downstream services.
        BigDecimal excessive = new BigDecimal("6000." + "9".repeat(513));

        ResponseEntity<String> response = post(KEY, excessive);

        assertThat(inventoryCalls.get()).as("inventory calls").isZero();
        assertThat(paymentCalls.get()).as("payment calls").isZero();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNothingHappened();
    }

    @Test
    void excessiveIntegerPrecisionIsRejectedBeforeAnyOperation() {
        // 11 integer digits do not fit NUMERIC(12,2) (10 integer digits).
        BigDecimal excessive = new BigDecimal("12345678901.00");

        ResponseEntity<String> response = post(KEY, excessive);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNothingHappened();
    }

    @Test
    void excessivePrecisionWithoutAKeyIsAlsoRejected() {
        BigDecimal excessive = new BigDecimal("6000." + "9".repeat(513));

        ResponseEntity<String> response = post(null, excessive);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNothingHappened();
    }

    @Test
    void maximumAcceptedPrecisionPassesValidationAndReachesPayment() {
        // 10 integer digits + 2 fractional digits is the exact schema bound;
        // it must pass validation and reach both downstream services.
        ResponseEntity<String> response =
                post(KEY, new BigDecimal("9999999999.99"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }
}
