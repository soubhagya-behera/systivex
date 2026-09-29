package com.soubhagya.systivex.gateway.api;

import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Small explicit edge: validates the checkout request and forwards it to
 * order-service over HTTP. Downstream 4xx responses (already sanitized by
 * order-service) pass through with their status; downstream 5xx or
 * connectivity failures become a generic 502. No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final RestClient orderClient;

    public CheckoutController(RestClient orderClient) {
        this.orderClient = orderClient;
    }

    @PostMapping
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) {
        try {
            String orderBody =
                    orderClient
                            .post()
                            .uri("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(String.class);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Objects.requireNonNullElse(orderBody, ""));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                return ResponseEntity.status(e.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Objects.requireNonNullElse(e.getResponseBodyAsString(), ""));
            }
            return badGateway();
        } catch (ResourceAccessException e) {
            return badGateway();
        }
    }

    private ResponseEntity<GatewayError> badGateway() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                        new GatewayError(
                                HttpStatus.BAD_GATEWAY.value(),
                                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                                "Checkout failed: order service unavailable"));
    }
}
