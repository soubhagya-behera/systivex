package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the inventory migration story end to end: the app boots against an
 * empty database, Flyway applies V1 (schema) + V2 (dev seed), and Hibernate
 * validation (ddl-auto=validate) accepts the migrated schema — otherwise
 * this context would not start.
 */
@SpringBootTest
class FlywayMigrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private Flyway flyway;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void migrationsAppliedSuccessfully() {
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(current.getState().isApplied()).isTrue();
    }

    @Test
    void migrationHistoryContainsV1AndV2() {
        assertThat(flyway.info().applied()).hasSize(2);
    }

    @Test
    void expectedConstraintsExist() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conname ="
                                        + " 'chk_inventory_quantity_non_negative'",
                                Integer.class))
                .isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_constraint WHERE conname ="
                                        + " 'chk_reservation_status'",
                                Integer.class))
                .isOne();
    }

    @Test
    void devSeedRowExists() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT available_quantity FROM inventory_items WHERE product_id ="
                                        + " 'SKU-1001'",
                                Integer.class))
                .isEqualTo(10);
    }
}
