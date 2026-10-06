package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
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
 * Observability slice: Prometheus exposition, business counters, and W3C
 * trace-context propagation to downstream stubs. No collector is needed —
 * propagation is proven by the traceparent headers the stubs receive, and
 * metrics by the scrape endpoint itself. Exporter formatting is not asserted,
 * only the presence of the metric families.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class ObservabilityTest extends AbstractPostgresIntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    private static HttpServer inventoryStub;
    private static HttpServer paymentStub;
    private static int inventoryPort;
    private static int paymentPort;
    private static final List<String> inventoryTraceparents = new CopyOnWriteArrayList<>();
    private static final List<String> paymentTraceparents = new CopyOnWriteArrayList<>();

    static {
        try {
            inventoryStub = stub(inventoryTraceparents, "inventory");
            paymentStub = stub(paymentTraceparents, "payment");
            inventoryPort = inventoryStub.getAddress().getPort();
            paymentPort = paymentStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub servers", e);
        }
    }

    private static HttpServer stub(List<String> captured, String which) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    String traceparent = exchange.getRequestHeaders().getFirst("traceparent");
                    if (traceparent != null) {
                        captured.add(traceparent);
                    }
                    String body;
                    if (which.equals("inventory")) {
                        body =
                                """
                                {"reservationId":"11111111-1111-1111-1111-111111111111",\
                                "productId":"SKU-1001","quantity":1,"status":"RESERVED"}\
                                """;
                    } else {
                        body =
                                """
                                {"authorizationId":"22222222-2222-2222-2222-222222222222",\
                                "customerId":"CUST-1001","amount":1499.00,"status":"AUTHORIZED"}\
                                """;
                    }
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

    @Autowired private TestRestTemplate rest;

    @Test
    void checkoutPropagatesIncomingTraceContextDownstream() {
        inventoryTraceparents.clear();
        paymentTraceparents.clear();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("traceparent", TRACEPARENT);
        OrderRequest body = new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
        ResponseEntity<String> response =
                rest.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Both downstream calls carry the SAME trace id the client sent —
        // the distributed trace survives both RestClient hops.
        assertThat(inventoryTraceparents).hasSize(1);
        assertThat(paymentTraceparents).hasSize(1);
        assertThat(inventoryTraceparents.get(0)).startsWith("00-" + TRACE_ID + "-");
        assertThat(paymentTraceparents.get(0)).startsWith("00-" + TRACE_ID + "-");
    }

    @Test
    void prometheusExposesHttpAndBusinessMetrics() {
        rest.postForEntity(
                "/api/v1/orders",
                new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                String.class);

        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_count");
        assertThat(metrics.getBody()).contains("checkout_success_total");
    }
}
