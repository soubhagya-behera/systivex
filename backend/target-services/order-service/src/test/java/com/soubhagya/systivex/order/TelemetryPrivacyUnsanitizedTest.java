package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Negative direction of the privacy regression: with the sanitizer bean
 * removed, the SAME synthetic downstream 422 MUST appear in recorded span
 * data. This proves the positive test is sensitive — it fails when the
 * sanitizer is absent (or registered too late to intercept the tracing
 * handler). Tokens are synthetic — no real data.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class TelemetryPrivacyUnsanitizedTest extends AbstractPostgresIntegrationTest {

    static final String[] TOKENS = {"CUSTOMER-TEST-XXXX", "PRODUCT-TEST-001", "AMOUNT-TEST-999"};

    @TestConfiguration
    static class CaptureWithoutSanitizer {

        @Bean
        TelemetryPrivacyTest.InMemorySpanExporter inMemorySpanExporter() {
            return new TelemetryPrivacyTest.InMemorySpanExporter();
        }

        @Bean
        SdkTracerProviderBuilderCustomizer inMemorySpanProcessor(
                TelemetryPrivacyTest.InMemorySpanExporter exporter) {
            return builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        }

        /**
         * Removes the sanitizer bean definition before instantiation, so the
         * observation pipeline runs exactly as it would with no sanitizer.
         * The (now memberless) sanitizer group stays and must not sanitize
         * anything on its own.
         */
        @Bean
        static BeanDefinitionRegistryPostProcessor removeSanitizer() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    for (String name : registry.getBeanDefinitionNames()) {
                        BeanDefinition definition = registry.getBeanDefinition(name);
                        if (ObservationErrorSanitizer.class
                                .getName()
                                .equals(definition.getBeanClassName())) {
                            registry.removeBeanDefinition(name);
                        }
                    }
                }
            };
        }
    }

    private static HttpServer inventoryStub;
    private static int inventoryPort;

    static {
        try {
            inventoryStub = HttpServer.create(new InetSocketAddress(0), 0);
            inventoryStub.createContext(
                    "/",
                    exchange -> {
                        exchange.getRequestBody().readAllBytes();
                        byte[] response =
                                """
                                {"status":422,"error":"Unprocessable Entity",\
                                "message":"rejected CUSTOMER-TEST-XXXX PRODUCT-TEST-001 AMOUNT-TEST-999"}\
                                """
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(422, response.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(response);
                        }
                    });
            inventoryStub.setExecutor(Executors.newCachedThreadPool());
            inventoryStub.start();
            inventoryPort = inventoryStub.getAddress().getPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub inventory-service", e);
        }
    }

    @DynamicPropertySource
    static void inventoryUrl(DynamicPropertyRegistry registry) {
        registry.add("app.inventory-service.url", () -> "http://localhost:" + inventoryPort);
    }

    @AfterAll
    static void stopStub() {
        inventoryStub.stop(0);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TelemetryPrivacyTest.InMemorySpanExporter exporter;

    @Test
    void withoutSanitizerPayloadReachesExportedSpanData() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);

        // Application behavior is unchanged without the sanitizer.
        assertThat(response.getStatusCode().value()).isEqualTo(422);

        List<SpanData> finished = awaitSpansWithCheckout();
        assertThat(finished.stream().map(SpanData::getName)).contains("checkout");

        List<String> texts = new ArrayList<>();
        finished.forEach(span -> texts.addAll(TelemetryPrivacyTest.spanTexts(span)));

        // Without the sanitizer the synthetic payload reaches span data —
        // the positive test would catch this.
        assertThat(texts)
                .anyMatch(
                        text ->
                                text.contains("CUSTOMER-TEST-XXXX")
                                        || text.contains("PRODUCT-TEST-001")
                                        || text.contains("AMOUNT-TEST-999"));
    }

    private List<SpanData> awaitSpansWithCheckout() {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            List<SpanData> current = exporter.spans();
            if (current.stream().anyMatch(span -> "checkout".equals(span.getName()))) {
                return current;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return exporter.spans();
    }
}
