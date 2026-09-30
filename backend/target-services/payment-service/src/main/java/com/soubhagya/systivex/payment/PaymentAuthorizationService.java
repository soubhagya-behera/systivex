package com.soubhagya.systivex.payment;

import com.soubhagya.systivex.payment.api.AuthorizationDeclinedException;
import com.soubhagya.systivex.payment.api.AuthorizeRequest;
import com.soubhagya.systivex.payment.api.AuthorizeResponse;
import com.soubhagya.systivex.payment.model.PaymentStatus;
import com.soubhagya.systivex.payment.model.PaymentTransaction;
import com.soubhagya.systivex.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic local authorization with persistent results. Amounts above
 * the demo limit are declined AND recorded as DECLINED — a decline is an
 * outcome, not a non-event. Still no payment-provider integration; no real
 * credentials anywhere near this class. The DECLINED row must commit even
 * though the method then throws, so the business rejection is excluded from
 * rollback — without this, recording a decline would always roll itself
 * back. One local payment-database transaction per attempt.
 */
@Service
public class PaymentAuthorizationService {

    static final BigDecimal DEMO_AUTH_LIMIT = new BigDecimal("5000.00");

    private final PaymentTransactionRepository payments;

    public PaymentAuthorizationService(PaymentTransactionRepository payments) {
        this.payments = payments;
    }

    @Transactional(noRollbackFor = AuthorizationDeclinedException.class)
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        UUID id = UUID.randomUUID();
        if (request.amount().compareTo(DEMO_AUTH_LIMIT) > 0) {
            payments.save(
                    new PaymentTransaction(
                            id,
                            request.customerId(),
                            request.orderReference(),
                            request.amount(),
                            PaymentStatus.DECLINED));
            throw new AuthorizationDeclinedException(
                    "Payment declined for customer "
                            + request.customerId()
                            + ": amount "
                            + request.amount()
                            + " exceeds the demo limit of "
                            + DEMO_AUTH_LIMIT);
        }
        payments.save(
                new PaymentTransaction(
                        id,
                        request.customerId(),
                        request.orderReference(),
                        request.amount(),
                        PaymentStatus.AUTHORIZED));
        return new AuthorizeResponse(id, request.customerId(), request.amount(), "AUTHORIZED");
    }
}
