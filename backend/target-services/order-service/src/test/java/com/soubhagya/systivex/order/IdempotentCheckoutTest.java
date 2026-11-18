package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.soubhagya.systivex.order.api.OrderResponse;
import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.model.OrderStatus;
import com.soubhagya.systivex.order.repository.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Idempotency contract over real HTTP, real PostgreSQL and stubbed
 * downstream services.
 *
 * <p>The invariants under test are the ones a client retry depends on: one
 * key owns one checkout, the downstream services are called once, the
 * recorded outcome is replayed instead of repeated, and a key that is reused
 * with a different payload is refused before anything happens downstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class IdempotentCheckoutTest extends AbstractPostgresIntegrationTest {

    private static final String KEY = "8f3c1d40-6a4e-4f0b-9d2e-1a2b3c4d5e6f";
    private static final String OTHER_KEY = "00000000-0000-4000-8000-000000000001";

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
    private static final AtomicInteger inventoryCalls = new AtomicInteger();
    private static final AtomicInteger paymentStatus = new AtomicInteger(201);
    private static final AtomicReference<String> paymentBody =
            new AtomicReference<>(PAYMENT_SUCCESS);
    private static final AtomicInteger paymentCalls = new AtomicInteger();
    /** Holds the inventory stub inside its call, to keep an attempt in flight. */
    private static final AtomicBoolean inventoryHold = new AtomicBoolean();

    private static final AtomicReference<CountDownLatch> inventoryGate =
            new AtomicReference<>(new CountDownLatch(0));

    static {
        try {
            inventoryStub = stub(inventoryStatus, inventoryBody, inventoryCalls, true);
            paymentStub = stub(paymentStatus, paymentBody, paymentCalls, false);
            inventoryPort = inventoryStub.getAddress().getPort();
            paymentPort = paymentStub.getAddress().getPort();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not start stub servers", e);
        }
    }

    private static HttpServer stub(
            AtomicInteger status,
            AtomicReference<String> body,
            AtomicInteger calls,
            boolean holdable) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        calls.incrementAndGet();
                        if (holdable && inventoryHold.get()) {
                            try {
                                inventoryGate.get().await(30, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
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
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub server", e);
        }
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
        inventoryStatus.set(201);
        inventoryBody.set(INVENTORY_SUCCESS);
        inventoryCalls.set(0);
        inventoryHold.set(false);
        inventoryGate.set(new CountDownLatch(0));
        paymentStatus.set(201);
        paymentBody.set(PAYMENT_SUCCESS);
        paymentCalls.set(0);
        orders.deleteAll();
    }

    @Autowired private TestRestTemplate rest;

    @Autowired private OrderRepository orders;

    @Autowired private JdbcTemplate jdbc;

    private OrderRequest checkoutRequest() {
        return new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    private ResponseEntity<OrderResponse> post(String key, OrderRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers),
                OrderResponse.class);
    }

    private ResponseEntity<String> postRaw(String key, OrderRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    // ---------------------------------------------------------------- replay

    @Test
    void sameKeyAndSamePayloadReplaysTheConfirmedResult() {
        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(first.getBody().orderId()).isNotNull();
        UUID orderId = first.getBody().orderId();

        ResponseEntity<OrderResponse> second = post(KEY, checkoutRequest());

        // The same successful result, not a new checkout.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().orderId()).isEqualTo(orderId);
        assertThat(second.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(second.getBody().reservationId())
                .isEqualTo(first.getBody().reservationId());
        assertThat(second.getBody().authorizationId())
                .isEqualTo(first.getBody().authorizationId());

        // One order, one reservation, one authorization.
        assertThat(orders.count()).isOne();
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();
    }

    @Test
    void replayedSuccessKeepsTheStoredOrderRow() {
        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());
        post(KEY, checkoutRequest());

        OrderEntity stored =
                orders.findByIdempotencyKeyHash(IdempotencyFingerprint.keyHash(KEY)).orElseThrow();
        assertThat(stored.getId()).isEqualTo(first.getBody().orderId());
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void replayIsJsonNumericallyEqualToTheOriginalResult() {
        // The fingerprint accepts any scale up to NUMERIC(12,2), so 1499.0
        // and 1499.00 describe the same payload. The replayed amount comes
        // back from the two-decimal column: its JSON numeric VALUE is the
        // same, but the textual scale may differ. Byte-identical replay is
        // not part of the contract.
        OrderRequest scaled =
                new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.0"));

        ResponseEntity<String> first = postRaw(KEY, scaled);
        ResponseEntity<String> second = postRaw(KEY, scaled);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(amountOf(second.getBody())).isEqualByComparingTo(amountOf(first.getBody()));
        assertThat(second.getBody())
                .contains("\"orderId\":\"" + orderIdOf(first.getBody()) + "\"");
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();
    }

    private static BigDecimal amountOf(String body) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"amount\":([^,}]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return new BigDecimal(matcher.group(1));
    }

    private static String orderIdOf(String body) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"orderId\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    // --------------------------------------------------------------- conflict

    @Test
    void sameKeyWithDifferentPayloadIsRefusedWithConflict() {
        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OrderRequest different =
                new OrderRequest("SKU-1001", 2, "CUST-1001", new BigDecimal("1499.00"));
        ResponseEntity<String> second = postRaw(KEY, different);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(second.getBody()).contains("409");

        // Nothing downstream happened for the refused request.
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }

    @Test
    void sameKeyWithDifferentAmountIsRefusedWithConflict() {
        post(KEY, checkoutRequest());

        OrderRequest different =
                new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("2499.00"));
        ResponseEntity<String> second = postRaw(KEY, different);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inventoryCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }

    @Test
    void sameKeyWithDifferentCustomerIsRefusedWithConflict() {
        post(KEY, checkoutRequest());

        OrderRequest different =
                new OrderRequest("SKU-1001", 1, "CUST-9999", new BigDecimal("1499.00"));
        ResponseEntity<String> second = postRaw(KEY, different);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inventoryCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }

    // ----------------------------------------------------------- failed replay

    @Test
    void failedInventoryCheckoutIsReplayedWithoutRepeatingTheBusinessOperation() {
        inventoryStatus.set(422);
        inventoryBody.set(BUSINESS_FAILURE);

        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());

        assertThat(first.getStatusCode().value()).isEqualTo(422);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().status()).isEqualTo("FAILED");
        assertThat(first.getBody().reason()).isEqualTo("INVENTORY_REJECTED");
        UUID orderId = first.getBody().orderId();

        ResponseEntity<OrderResponse> second = post(KEY, checkoutRequest());

        // The original failure outcome, reproduced - not a second attempt.
        assertThat(second.getStatusCode().value()).isEqualTo(422);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().status()).isEqualTo("FAILED");
        assertThat(second.getBody().reason()).isEqualTo("INVENTORY_REJECTED");
        assertThat(second.getBody().orderId()).isEqualTo(orderId);
        assertThat(second.getBody().message()).isEqualTo(first.getBody().message());

        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isZero();
        assertThat(orders.count()).isOne();

        OrderEntity stored =
                orders.findByIdempotencyKeyHash(IdempotencyFingerprint.keyHash(KEY)).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo("INVENTORY_REJECTED");
    }

    @Test
    void failedPaymentCheckoutIsReplayedWithoutRepeatingTheBusinessOperation() {
        paymentStatus.set(422);
        paymentBody.set(BUSINESS_FAILURE);

        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());

        assertThat(first.getStatusCode().value()).isEqualTo(422);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().reason()).isEqualTo("PAYMENT_DECLINED");

        ResponseEntity<OrderResponse> second = post(KEY, checkoutRequest());

        assertThat(second.getStatusCode().value()).isEqualTo(422);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().reason()).isEqualTo("PAYMENT_DECLINED");
        assertThat(second.getBody().orderId()).isEqualTo(first.getBody().orderId());

        assertThat(paymentCalls.get()).isOne();
        assertThat(inventoryCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }

    @Test
    void failedDownstreamCheckoutIsReplayedWithoutRepeatingTheBusinessOperation() {
        paymentStatus.set(500);
        paymentBody.set("{}");

        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().reason()).isEqualTo("DOWNSTREAM_UNAVAILABLE");

        ResponseEntity<OrderResponse> second = post(KEY, checkoutRequest());

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().reason()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(second.getBody().orderId()).isEqualTo(first.getBody().orderId());

        assertThat(paymentCalls.get()).isOne();
        assertThat(orders.count()).isOne();
    }

    // ---------------------------------------------------------------- pending

    @Test
    void duplicateWhileTheOriginalIsPendingIsRefusedWithConflict() {
        // A PENDING row is the fingerprint of an attempt that owns the key
        // and has not finished yet.
        orders.save(
                new OrderEntity(
                        UUID.randomUUID(),
                        "CUST-1001",
                        "SKU-1001",
                        1,
                        new BigDecimal("1499.00"),
                        IdempotencyFingerprint.keyHash(KEY),
                        IdempotencyFingerprint.requestHash(checkoutRequest())));

        ResponseEntity<String> response = postRaw(KEY, checkoutRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("IDEMPOTENCY_KEY_IN_PROGRESS");

        // No second order, and not a single downstream call.
        assertThat(orders.count()).isOne();
        assertThat(inventoryCalls.get()).isZero();
        assertThat(paymentCalls.get()).isZero();
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void concurrentInsertsForTheSameKeyElectExactlyOneOwner() throws Exception {
        // The database, not application timing, decides the owner.
        String keyHash = IdempotencyFingerprint.keyHash("race-key");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    orders.saveAndFlush(
                                            new OrderEntity(
                                                    UUID.randomUUID(),
                                                    "CUST-1001",
                                                    "SKU-1001",
                                                    1,
                                                    new BigDecimal("1499.00"),
                                                    keyHash,
                                                    "request-hash"));
                                    return true;
                                } catch (DataIntegrityViolationException lost) {
                                    return false;
                                }
                            }));
        }
        go.countDown();

        long winners = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(60, TimeUnit.SECONDS)) {
                winners++;
            }
        }
        pool.shutdownNow();

        assertThat(winners).isOne();
        assertThat(orders.findByIdempotencyKeyHash(keyHash)).isPresent();
    }

    @Test
    void duplicateWhileTheFirstAttemptIsStillRunningIsRefusedWithConflict() throws Exception {
        // The owner is held inside the inventory call, so the attempt is
        // demonstrably in flight while the duplicate arrives.
        CountDownLatch gate = new CountDownLatch(1);
        inventoryGate.set(gate);
        inventoryHold.set(true);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> owner = pool.submit(() -> postRaw(KEY, checkoutRequest()));

            long deadline = System.currentTimeMillis() + 20_000;
            while (inventoryCalls.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(inventoryCalls.get()).isOne();

            ResponseEntity<String> duplicate = postRaw(KEY, checkoutRequest());

            // The duplicate must not start a second checkout.
            assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(duplicate.getBody()).contains("IDEMPOTENCY_KEY_IN_PROGRESS");
            assertThat(inventoryCalls.get()).isOne();

            gate.countDown();
            ResponseEntity<String> ownerResponse = owner.get(60, TimeUnit.SECONDS);
            assertThat(ownerResponse.getStatusCode().value()).isEqualTo(201);
        } finally {
            gate.countDown();
            inventoryHold.set(false);
            pool.shutdownNow();
        }

        // One order, one reservation, one authorization for the whole key.
        assertThat(orders.count()).isOne();
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();
    }

    @Test
    void concurrentRequestsWithTheSameKeyOwnOneCheckout() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(
                    pool.submit(
                            () -> {
                                ready.countDown();
                                go.await();
                                return postRaw(KEY, checkoutRequest());
                            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        go.countDown();

        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        // Exactly one order row can own the key - the unique index decides.
        assertThat(orders.count()).isOne();
        assertThat(orders.findByIdempotencyKeyHash(IdempotencyFingerprint.keyHash(KEY)))
                .isPresent();

        // The downstream services were called exactly once each.
        assertThat(inventoryCalls.get()).isOne();
        assertThat(paymentCalls.get()).isOne();

        // Every answer is either the original result or an explicit conflict,
        // and all successful answers carry the same order id.
        Set<String> statuses =
                responses.stream()
                        .map(response -> String.valueOf(response.getStatusCode().value()))
                        .collect(Collectors.toSet());
        assertThat(statuses).isNotEmpty();
        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn("201", "409"));

        List<String> successBodies =
                responses.stream()
                        .filter(response -> response.getStatusCode().value() == 201)
                        .map(ResponseEntity::getBody)
                        .toList();
        assertThat(successBodies).isNotEmpty();
        Set<String> orderIds =
                successBodies.stream()
                        .map(body -> body.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1"))
                        .collect(Collectors.toSet());
        assertThat(orderIds).hasSize(1);
    }

    // --------------------------------------------------------------- legacy

    @Test
    void requestWithoutKeyKeepsTheLegacyBehaviour() {
        ResponseEntity<OrderResponse> first = post(null, checkoutRequest());
        ResponseEntity<OrderResponse> second = post(null, checkoutRequest());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();

        // Two requests, two orders - that is what the contract says for an
        // unkeyed request.
        assertThat(first.getBody().orderId()).isNotEqualTo(second.getBody().orderId());
        assertThat(orders.count()).isEqualTo(2);
        assertThat(inventoryCalls.get()).isEqualTo(2);
        assertThat(paymentCalls.get()).isEqualTo(2);

        // No idempotency linkage is stored for an unkeyed request.
        Long keyedRows =
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE idempotency_key_hash IS NOT NULL",
                        Long.class);
        assertThat(keyedRows).isZero();
    }

    @Test
    void twoDifferentKeysCreateTwoOrders() {
        ResponseEntity<OrderResponse> first = post(KEY, checkoutRequest());
        ResponseEntity<OrderResponse> second = post(OTHER_KEY, checkoutRequest());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(first.getBody().orderId()).isNotEqualTo(second.getBody().orderId());
        assertThat(orders.count()).isEqualTo(2);
        assertThat(inventoryCalls.get()).isEqualTo(2);
    }

    // ------------------------------------------------------------ key hygiene

    @Test
    void theKeyIsNeverPersistedInClearText() {
        post(KEY, checkoutRequest());

        String stored =
                jdbc.queryForObject(
                        "SELECT idempotency_key_hash FROM orders WHERE idempotency_key_hash IS NOT NULL",
                        String.class);
        assertThat(stored).isNotNull();
        assertThat(stored).isEqualTo(IdempotencyFingerprint.keyHash(KEY));
        assertThat(stored).doesNotContain(KEY);
        assertThat(stored).hasSize(64);
    }

    @Test
    void blankKeyIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "   ");

        ResponseEntity<String> response =
                rest.exchange(
                        "/api/v1/orders",
                        HttpMethod.POST,
                        new HttpEntity<>(checkoutRequest(), headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Idempotency-Key");
        assertThat(response.getBody()).doesNotContain("   ");
        assertThat(orders.count()).isZero();
        assertThat(inventoryCalls.get()).isZero();
    }

    @Test
    void oversizedKeyIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "k".repeat(256));

        ResponseEntity<String> response =
                rest.exchange(
                        "/api/v1/orders",
                        HttpMethod.POST,
                        new HttpEntity<>(checkoutRequest(), headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orders.count()).isZero();
    }

    @Test
    void malformedBodyIsRejectedBeforeAnyIdempotencyStateIsWritten() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", KEY);

        ResponseEntity<String> response =
                rest.exchange(
                        "/api/v1/orders",
                        HttpMethod.POST,
                        new HttpEntity<>(new OrderRequest("", 0, "", null), headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orders.count()).isZero();
        assertThat(inventoryCalls.get()).isZero();
    }
}
