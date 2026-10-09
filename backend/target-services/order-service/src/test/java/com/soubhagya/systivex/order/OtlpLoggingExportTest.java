package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Phase 3B-2 slice: the OTLP log-export path is wired and harmless.
 *
 * <p>Boots the service with the same logging properties the local
 * configuration uses ({@code management.logging.export.otlp.enabled=true} and
 * an OTLP/HTTP logs endpoint) and asserts the SDK logger provider is built.
 * No collector or Loki runs here, so OTLP delivery fails in the background
 * batch processor — the checkout below proves that failure never breaks the
 * request path. Log content itself is verified live against Loki, not here.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0",
            "management.logging.export.otlp.enabled=true",
            "management.opentelemetry.logging.export.otlp.endpoint=http://localhost:4318/v1/logs"
        })
@AutoConfigureTestRestTemplate
class OtlpLoggingExportTest extends AbstractPostgresIntegrationTest {

    private static HttpServer inventoryStub;
    private static HttpServer paymentStub;
    private static int inventoryPort;
    private static int paymentPort;

    static {
        try {
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
    @Autowired private Environment environment;
    @Autowired private ObjectProvider<SdkLoggerProvider> loggerProviders;

    @Test
    void otlpLogExportIsEnabled() {
        assertThat(environment.getProperty("management.logging.export.otlp.enabled", Boolean.class))
                .isTrue();
        assertThat(loggerProviders.getIfAvailable()).isNotNull();
    }

    @Test
    void checkoutSucceedsWhileLogExportHasNoCollector() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
