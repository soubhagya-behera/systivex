package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the order migration story end to end: the app boots against an
 * empty database, Flyway applies the schema, and Hibernate validation
 * (ddl-auto=validate) accepts the migrated schema — otherwise this context
 * would not start.
 */
@SpringBootTest
class FlywayMigrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void latestMigrationAppliedSuccessfully() {
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(current.getState().isApplied()).isTrue();
    }

    @Test
    void migrationHistoryContainsV1AndV2() {
        assertThat(flyway.info().applied()).hasSize(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(
                        Arrays.stream(flyway.info().applied())
                                .map(info -> info.getVersion().getVersion())
                                .toList())
                .containsExactly("1", "2");
    }

    @Test
    void expectedConstraintsExist() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conname = 'chk_orders_status'",
                                Integer.class))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conname = 'chk_orders_quantity_positive'",
                                Integer.class))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conname = 'chk_orders_amount_positive'",
                                Integer.class))
                .isOne();
    }

    @Test
    void idempotencyColumnsExist() {
        List<String> columns =
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_name = 'orders' ORDER BY column_name",
                        String.class);
        assertThat(columns)
                .contains(
                        "idempotency_key_hash",
                        "idempotency_request_hash",
                        "failure_reason",
                        "failure_message");
    }

    @Test
    void idempotencyKeyIndexIsUnique() {
        Integer unique =
                jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE tablename = 'orders'"
                                + " AND indexname = 'uq_orders_idempotency_key'"
                                + " AND indexdef LIKE '%UNIQUE%'",
                        Integer.class);
        assertThat(unique).isOne();
    }
}
