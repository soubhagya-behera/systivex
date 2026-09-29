package com.soubhagya.systivex.payment.api;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deterministic, stateless authorization endpoint for Phase 2A. There is no
 * ledger yet: any amount above {@value #DEMO_AUTH_LIMIT} is declined so
 * callers can exercise the failure path. A real payment provider arrives
 * in a later phase.
 */
@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentController {

    static final BigDecimal DEMO_AUTH_LIMIT = new BigDecimal("5000.00");

    @PostMapping("/authorize")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizeResponse authorize(@Valid @RequestBody AuthorizeRequest request) {
        if (request.amount().compareTo(DEMO_AUTH_LIMIT) > 0) {
            throw new AuthorizationDeclinedException(
                    "Payment declined for customer "
                            + request.customerId()
                            + ": amount "
                            + request.amount()
                            + " exceeds the Phase 2A demo limit of "
                            + DEMO_AUTH_LIMIT);
        }
        return new AuthorizeResponse(
                UUID.randomUUID(), request.customerId(), request.amount(), "AUTHORIZED");
    }
}
