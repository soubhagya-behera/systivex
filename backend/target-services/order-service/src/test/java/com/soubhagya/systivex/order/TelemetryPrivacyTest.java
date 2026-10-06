package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.order.api.OrderRequest;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Privacy regression for the Phase 3A redaction rule: a downstream 422 whose
 * body carries synthetic business tokens must not leak those tokens into
 * RECORDED SPAN DATA (the OTel bridge copies observation errors into span
 * exception events, so whatever the tracing handler receives is what gets
 * exported). Tokens are synthetic — no real data.
 *
 * <p>Why span data, not just the observation error: a previous implementation
 * sanitized only the final recorded error while the tracing handler had
 * already captured the raw exception event (Boot registers tracing handlers
 * in a group ahead of ungrouped handlers). That version passed a final-error
 * assertion and still leaked live. This test captures finished spans
 * in-memory and asserts on event attributes, span attributes, and the span
 * status description — the fields that actually leave the process.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class TelemetryPrivacyTest extends AbstractPostgresIntegrationTest {

    static final String[] TOKENS = {"CUSTOMER-TEST-XXXX", "PRODUCT-TEST-001", "AMOUNT-TEST-999"};

    static final AttributeKey<String> EXCEPTION_MESSAGE =
            AttributeKey.stringKey("exception.message");

    /** Errors recorded by the capturing handler, in notification order. */
    static final List<RecordedError> ERRORS = new CopyOnWriteArrayList<>();

    record RecordedError(String observation, String message, String traceId) {}

    /** OTLP-independent finished-span capture for assertions on real span data. */
    static final class InMemorySpanExporter implements SpanExporter {
        private final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        List<SpanData> spans() {
            return List.copyOf(spans);
        }

        /**
         * Drops spans from earlier requests in the same context. {@link
         * SimpleSpanProcessor} exports synchronously on span end, which happens
         * before the HTTP response reaches the caller, so a reset immediately
         * before a request leaves exactly that request's spans behind.
         */
        void reset() {
            spans.clear();
        }
    }

    @TestConfiguration
    static class TelemetryCapture {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return new InMemorySpanExporter();
        }

        @Bean
        SdkTracerProviderBuilderCustomizer inMemorySpanProcessor(InMemorySpanExporter exporter) {
            return builder -> builder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        }

        /**
         * Captures observation errors after every other handler has run
         * (default order, so after both the sanitizer and the tracing
         * handler) — what is recorded here is the final observation error.
         */
        @Bean
        ObservationHandler<Observation.Context> capturingHandler(Tracer tracer) {
            return new ObservationHandler<Observation.Context>() {
                @Override
                public void onError(Observation.Context context) {
                    String traceId = null;
                    Span current = tracer.currentSpan();
                    if (current != null) {
                        traceId = current.context().traceId();
                    }
                    Throwable error = context.getError();
                    ERRORS.add(
                            new RecordedError(
                                    context.getName(),
                                    error == null ? null : error.getMessage(),
                                    traceId));
                }

                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
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
    private InMemorySpanExporter exporter;

    @Test
    void downstreamBusinessPayloadDoesNotReachRecordedObservationErrors() {
        ERRORS.clear();

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(ERRORS).isNotEmpty();

        // No recorded error carries the downstream business payload.
        assertThat(ERRORS)
                .allSatisfy(
                        error ->
                                assertThat(error.message()).doesNotContain(TOKENS));

        // Useful telemetry survives: HTTP status, the service-defined
        // observation, and a single shared trace across all errors.
        assertThat(ERRORS.stream().map(RecordedError::message))
                .anySatisfy(message -> assertThat(message).contains("422"));
        assertThat(ERRORS.stream().map(RecordedError::observation)).contains("checkout");
        assertThat(ERRORS.stream().map(RecordedError::traceId))
                .allSatisfy(traceId -> assertThat(traceId).matches("[0-9a-f]{32}"));
        assertThat(ERRORS.stream().map(RecordedError::traceId).distinct().count()).isEqualTo(1);
    }

    @Test
    void downstreamBusinessPayloadDoesNotReachExportedSpanData() {
        exporter.reset();

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);

        List<SpanData> finished = awaitSpansWithCheckout();
        assertThat(finished.stream().map(SpanData::getName)).contains("checkout");

        List<String> texts = new ArrayList<>();
        finished.forEach(span -> texts.addAll(spanTexts(span)));

        // None of the synthetic tokens may appear in span names, span
        // attributes, exception event attributes, or the status description.
        assertThat(texts).allSatisfy(text -> assertThat(text).doesNotContain(TOKENS));

        // Useful telemetry remains: an error status, the HTTP status, the
        // business observation, and a single shared trace across spans.
        assertThat(finished.stream().map(span -> span.getStatus().getStatusCode()))
                .contains(StatusCode.ERROR);
        assertThat(texts).anyMatch(text -> text.contains("422"));
        assertThat(
                        finished.stream()
                                .map(span -> span.getSpanContext().getTraceId())
                                .distinct()
                                .count())
                .isEqualTo(1);
        assertThat(finished.stream().map(span -> span.getSpanContext().getTraceId()))
                .allSatisfy(traceId -> assertThat(traceId).matches("[0-9a-f]{32}"));
    }

    /**
     * Error telemetry must survive, not be switched off. Disabling tracing,
     * error events, or RestClient observations would also make the assertions
     * above pass, so this pins the parts that must still be exported: the
     * {@code exception} event, its {@code exception.type} /
     * {@code exception.message} / {@code exception.stacktrace} attributes, and
     * the HTTP classification inside them.
     */
    @Test
    void sanitizedExceptionEventsRemainPresentAndUseful() {
        exporter.reset();

        ResponseEntity<String> response =
                rest.postForEntity(
                        "/api/v1/orders",
                        new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00")),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(422);

        List<SpanData> finished = awaitSpansWithCheckout();
        List<EventData> exceptionEvents =
                finished.stream()
                        .flatMap(span -> span.getEvents().stream())
                        .filter(event -> "exception".equals(event.getName()))
                        .toList();

        // The exception event is still recorded, with all three attributes.
        assertThat(exceptionEvents).isNotEmpty();
        assertThat(exceptionEvents)
                .allSatisfy(
                        event ->
                                assertThat(
                                                event.getAttributes().asMap().keySet().stream()
                                                        .map(AttributeKey::getKey)
                                                        .toList())
                                        .contains("exception.type", "exception.message", "exception.stacktrace"));

        List<String> messages =
                exceptionEvents.stream()
                        .map(event -> event.getAttributes().get(EXCEPTION_MESSAGE))
                        .filter(Objects::nonNull)
                        .toList();
        assertThat(messages).isNotEmpty();
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(TOKENS));

        // Useful classification is deliberately retained in the sanitized
        // message: the downstream HTTP status on the client hop, and the
        // original exception type name on the business hop.
        assertThat(messages)
                .anySatisfy(
                        message ->
                                assertThat(message)
                                        .contains("422")
                                        .contains("withheld from telemetry"));
        assertThat(messages)
                .anySatisfy(
                        message -> assertThat(message).contains("InventoryRejectedException"));
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

    /** Every free-text field of a span that an exporter would carry out. */
    static List<String> spanTexts(SpanData span) {
        List<String> texts = new ArrayList<>();
        texts.add(span.getName());
        if (span.getStatus().getDescription() != null) {
            texts.add(span.getStatus().getDescription());
        }
        span.getAttributes().asMap().values().forEach(value -> texts.add(String.valueOf(value)));
        for (EventData event : span.getEvents()) {
            texts.add(event.getName());
            event.getAttributes().asMap().values().forEach(value -> texts.add(String.valueOf(value)));
        }
        return texts;
    }
}
