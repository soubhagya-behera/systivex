package com.soubhagya.systivex.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.payment.api.AuthorizeRequest;
import com.soubhagya.systivex.payment.api.AuthorizeResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PaymentServiceTest {

    @Autowired private TestRestTemplate rest;

    @Test
    void authorizesWithinDemoLimit() {
        ResponseEntity<AuthorizeResponse> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", new BigDecimal("1499.00")),
                        AuthorizeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authorizationId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("AUTHORIZED");
    }

    @Test
    void declinesAboveDemoLimitWith422() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", new BigDecimal("6000.00")),
                        String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("declined");
    }

    @Test
    void rejectsMissingAmountWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", null),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsZeroAmountWith400() {
        ResponseEntity<String> response =
                rest.postForEntity(
                        "/internal/v1/payments/authorize",
                        new AuthorizeRequest("CUST-1001", new BigDecimal("0.00")),
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
