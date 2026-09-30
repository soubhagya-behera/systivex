package com.soubhagya.systivex.payment.api;

import com.soubhagya.systivex.payment.PaymentAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persistent authorization endpoint for Phase 2B. The approve/decline rule is
 * still the deterministic demo limit (a real payment provider arrives in a
 * later phase); what changed is that every attempt is now recorded in the
 * payment-service database.
 */
@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentController {

    private final PaymentAuthorizationService authorizations;

    public PaymentController(PaymentAuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @PostMapping("/authorize")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizeResponse authorize(@Valid @RequestBody AuthorizeRequest request) {
        return authorizations.authorize(request);
    }
}
