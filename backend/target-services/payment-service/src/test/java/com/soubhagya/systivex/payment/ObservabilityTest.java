package com.soubhagya.systivex.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.payment.api.AuthorizeRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Observability slice: Prometheus exposition plus authorization counters for
 * both outcomes. No collector needed; only metric-family presence is asserted.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=health,prometheus",
            "management.tracing.sampling.probability=1.0"
        })
@AutoConfigureTestRestTemplate
class ObservabilityTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @BeforeEach
    void authorizeBothOutcomes() {
        rest.postForEntity(
                "/internal/v1/payments/authorize",
                new AuthorizeRequest("CUST-1001", "order-obs", new BigDecimal("1499.00")),
                String.class);
        rest.postForEntity(
                "/internal/v1/payments/authorize",
                new AuthorizeRequest("CUST-1001", "order-obs", new BigDecimal("6000.00")),
                String.class);
    }

    @Test
    void prometheusExposesHttpAndAuthorizationMetrics() {
        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_count");
        assertThat(metrics.getBody()).contains("payment_authorization_success_total");
        assertThat(metrics.getBody()).contains("payment_authorization_declined_total");
    }
}
