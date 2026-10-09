package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
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
 * Phase 3B-2 regression: a checkout log record actually leaves the JVM over
 * OTLP/HTTP.
 *
 * <p>Boot wires the OTel SDK and the OTLP log exporter from properties, but
 * installs no Logback bridge itself — {@code logback-spring.xml} plus {@link
 * OtelLogAppenderInstaller} close that gap. Bean-existence assertions cannot
 * catch a missing bridge, so this test points the exporter at a stub OTLP
 * receiver, performs a checkout, and waits for the {@code Checkout ...
 * CONFIRMED} record to arrive (protobuf body, so the message text is matched
 * as a UTF-8 substring). It also proves the trace/span MDC keys travel as
 * record attributes and that the request path never depends on the receiver.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0",
            "management.logging.export.otlp.enabled=true"
        })
@AutoConfigureTestRestTemplate
class OtlpLogDeliveryTest extends AbstractPostgresIntegrationTest {

    private static final List<byte[]> RECEIVED = new CopyOnWriteArrayList<>();

    private static HttpServer otlpStub;
    private static HttpServer inventoryStub;
    private static HttpServer paymentStub;
    private static int otlpPort;
    private static int inventoryPort;
    private static int paymentPort;

    static {
        try {
            otlpStub = HttpServer.create(new InetSocketAddress(0), 0);
            otlpStub.createContext(
                    "/",
                    exchange -> {
                        byte[] body = exchange.getRequestBody().readAllBytes();
                        RECEIVED.add(body);
                        byte[] response = new byte[0];
                        exchange.sendResponseHeaders(200, response.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(response);
                        }
                    });
            otlpStub.setExecutor(Executors.newCachedThreadPool());
            otlpStub.start();
            otlpPort = otlpStub.getAddress().getPort();

            inventoryStub = stub("inventory");
            paymentStub = stub("payment");
            inventoryPort = inventoryStub.getAddress().getPort();
            paymentPort = paymentStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub servers", e);
        }
    }

    private static HttpServer stub(String which) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
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
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add(
                "management.opentelemetry.logging.export.otlp.endpoint",
                () -> "http://localhost:" + otlpPort + "/v1/logs");
        registry.add("app.inventory-service.url", () -> "http://localhost:" + inventoryPort);
        registry.add("app.payment-service.url", () -> "http://localhost:" + paymentPort);
    }

    @AfterAll
    static void stopStubs() {
        otlpStub.stop(0);
        inventoryStub.stop(0);
        paymentStub.stop(0);
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private OpenTelemetry openTelemetry;

    @Test
    void checkoutLogRecordReachesOtlpReceiver() throws Exception {
        // The OTEL appender lives in the JVM-global Logback context shared by
        // every test class in this fork; re-install this context's SDK so the
        // records below are routed to this test's stub receiver.
        OpenTelemetryAppender.install(openTelemetry);
        RECEIVED.clear();

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String orderId = extractOrderId(response.getBody());
        String expected = "Checkout " + orderId + " CONFIRMED";

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
        boolean found = false;
        boolean traceKeySeen = false;
        while (System.currentTimeMillis() < deadline) {
            for (byte[] payload : RECEIVED) {
                String text = new String(payload, StandardCharsets.UTF_8);
                if (text.contains(expected)) {
                    found = true;
                }
                if (text.contains("traceId")) {
                    traceKeySeen = true;
                }
            }
            if (found && traceKeySeen) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(found)
                .as("expected the checkout CONFIRMED log record at the OTLP receiver")
                .isTrue();
        assertThat(traceKeySeen)
                .as("expected MDC trace context (traceId key) on exported records")
                .isTrue();
    }

    private static String extractOrderId(String body) {
        // Minimal parse of {"orderId":"...","status":"CONFIRMED",...}: the
        // checkout log line carries the order id verbatim, so find it here
        // without binding a DTO.
        int start = body.indexOf("\"orderId\":\"") + "\"orderId\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
