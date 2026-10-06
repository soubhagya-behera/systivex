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
 * Gateway observability slice: the gateway must forward the incoming W3C
 * trace context to order-service (same trace id on the stubbed hop), and the
 * Prometheus endpoint must expose HTTP metrics. No collector needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class ObservabilityTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static HttpServer orderStub;
    private static int orderPort;
    private static final List<String> orderTraceparents = new CopyOnWriteArrayList<>();

    static {
        try {
            orderStub = HttpServer.create(new InetSocketAddress(0), 0);
            orderStub.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        String traceparent =
                                exchange.getRequestHeaders().getFirst("traceparent");
                        if (traceparent != null) {
                            orderTraceparents.add(traceparent);
                        }
                        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
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

    @Autowired private TestRestTemplate rest;

    @Test
    void gatewayPropagatesIncomingTraceContextToOrder() {
        orderTraceparents.clear();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01");
        CheckoutRequest body =
                new CheckoutRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
        ResponseEntity<String> response =
                rest.exchange(
                        "/api/v1/checkout", HttpMethod.POST, new HttpEntity<>(body, headers),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(orderTraceparents).hasSize(1);
        assertThat(orderTraceparents.get(0)).startsWith("00-" + TRACE_ID + "-");
    }

    @Test
    void prometheusExposesHttpMetrics() {
        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_count");
    }
}
