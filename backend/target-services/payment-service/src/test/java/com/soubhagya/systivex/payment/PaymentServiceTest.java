package com.soubhagya.systivex.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.payment.api.AuthorizeRequest;
import com.soubhagya.systivex.payment.api.AuthorizeResponse;
import com.soubhagya.systivex.payment.model.PaymentStatus;
import com.soubhagya.systivex.payment.model.PaymentTransaction;
import com.soubhagya.systivex.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Authorization boundary tests over real HTTP, now backed by a throwaway
 * PostgreSQL (Flyway-migrated): every attempt also asserts the persisted
 * payment row. Nothing here touches the developer's local payment_db.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PaymentServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Autowired private PaymentTransactionRepository payments;

    @BeforeEach
    void cleanPayments() {
        payments.deleteAll();
    }

    @Test
    void authorizesWithinDemoLimitAndPersists() {
        ResponseEntity<AuthorizeResponse> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", "order-1", new BigDecimal("1499.00")),
                        AuthorizeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authorizationId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("AUTHORIZED");

        PaymentTransaction persisted =
                payments.findById(response.getBody().authorizationId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(persisted.getOrderReference()).isEqualTo("order-1");
        assertThat(persisted.getAmount()).isEqualByComparingTo("1499.00");
    }

    @Test
    void declinesAboveDemoLimitAndPersistsDeclineWith422() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", "order-2", new BigDecimal("6000.00")),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("declined");

        // A decline is a recorded outcome, not a non-event.
        List<PaymentTransaction> all = payments.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(all.get(0).getOrderReference()).isEqualTo("order-2");
    }

    @Test
    void rejectsMissingAmountWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", null, null),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsZeroAmountWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", null, new BigDecimal("0.00")),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response =
                rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
