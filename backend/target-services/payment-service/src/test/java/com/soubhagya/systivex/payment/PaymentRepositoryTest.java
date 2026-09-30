package com.soubhagya.systivex.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.payment.model.PaymentStatus;
import com.soubhagya.systivex.payment.model.PaymentTransaction;
import com.soubhagya.systivex.payment.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PaymentRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired private PaymentTransactionRepository payments;

    @Autowired private EntityManager entityManager;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void persistsAuthorizedTransaction() {
        PaymentTransaction saved =
                payments.saveAndFlush(
                        new PaymentTransaction(
                                UUID.randomUUID(),
                                "CUST-1001",
                                "order-1",
                                new BigDecimal("1499.00"),
                                PaymentStatus.AUTHORIZED));

        entityManager.clear();

        PaymentTransaction found = payments.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(found.getOrderReference()).isEqualTo("order-1");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsDeclinedTransaction() {
        PaymentTransaction saved =
                payments.saveAndFlush(
                        new PaymentTransaction(
                                UUID.randomUUID(),
                                "CUST-1001",
                                null,
                                new BigDecimal("6000.00"),
                                PaymentStatus.DECLINED));

        entityManager.clear();

        PaymentTransaction found = payments.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(found.getOrderReference()).isNull();
    }

    @Test
    void databaseRejectsUnknownStatus() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO payments (id, customer_id, amount, status)"
                                                + " VALUES (?, 'C', 1.00, 'PENDING')",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
