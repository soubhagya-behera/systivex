package com.soubhagya.systivex.twin;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Control-plane observability slice: tracing is active (a real tracer backs
 * observations) and health stays green. Metrics export is deliberately
 * disabled in tests by Boot's metrics-test support, so the Prometheus scrape
 * endpoint is not asserted here — it is verified against the live control
 * plane instead. Nothing here stores or analyzes telemetry.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureTestRestTemplate
class ObservabilityTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Autowired private Tracer tracer;

    @Autowired private ObservationRegistry observations;

    @Test
    void tracingProducesRealSpans() {
        Observation.createNotStarted("twin.probe", observations)
                .observe(
                        () -> {
                            assertThat(tracer.currentSpan()).isNotNull();
                            assertThat(tracer.currentSpan().context().traceId())
                                    .hasSize(32);
                        });
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
