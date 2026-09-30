package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.order.model.OrderEntity;
import com.soubhagya.systivex.order.model.OrderStatus;
import com.soubhagya.systivex.order.repository.OrderRepository;
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
class OrderRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired private OrderRepository orders;

    @Autowired private EntityManager entityManager;

    @Autowired private JdbcTemplate jdbc;

    private OrderEntity newOrder() {
        return new OrderEntity(
                UUID.randomUUID(), "CUST-1001", "SKU-1001", 1, new BigDecimal("1499.00"));
    }

    @Test
    void persistsPendingOrderAndFindsIt() {
        OrderEntity saved = orders.saveAndFlush(newOrder());

        entityManager.clear();

        OrderEntity found = orders.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getCustomerId()).isEqualTo("CUST-1001");
        assertThat(found.getReservationId()).isNull();
        assertThat(found.getAuthorizationId()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void pendingToConfirmedTransition() {
        OrderEntity saved = orders.saveAndFlush(newOrder());

        saved.confirm(UUID.randomUUID(), UUID.randomUUID());
        orders.saveAndFlush(saved);
        entityManager.clear();

        OrderEntity found = orders.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(found.getReservationId()).isNotNull();
        assertThat(found.getAuthorizationId()).isNotNull();
    }

    @Test
    void pendingToFailedTransition() {
        OrderEntity saved = orders.saveAndFlush(newOrder());

        saved.fail();
        orders.saveAndFlush(saved);
        entityManager.clear();

        OrderEntity found = orders.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(found.getReservationId()).isNull();
    }

    @Test
    void databaseRejectsUnknownStatus() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO orders (id, customer_id, product_id, quantity,"
                                                + " amount, status) VALUES (?, 'C', 'P', 1, 1.00,"
                                                + " 'SHIPPED')",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositiveQuantity() {
        OrderEntity bad =
                new OrderEntity(UUID.randomUUID(), "CUST-1001", "SKU-1001", 0, new BigDecimal("1.00"));
        assertThatThrownBy(() -> orders.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
