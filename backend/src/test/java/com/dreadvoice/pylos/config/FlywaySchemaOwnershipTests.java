package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:flyway-ownership;DB_CLOSE_DELAY=-1",
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class FlywaySchemaOwnershipTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayOwnsTheSchemaAndRecordsItsHistory() {
        Integer historyTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) = ?",
                Integer.class,
                "FLYWAY_SCHEMA_HISTORY");

        assertThat(historyTables).isEqualTo(1);
    }

    @Test
    void migrationsCreateThePendingActionTable() {
        Integer pendingActionTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) = ?",
                Integer.class,
                "PENDING_ACTION");

        assertThat(pendingActionTables).isEqualTo(1);
    }

    @Test
    void appliedMigrationsAreRecorded() {
        List<String> applied = jdbcTemplate.queryForList(
                "select \"script\" from \"flyway_schema_history\" order by \"installed_rank\"",
                String.class);

        assertThat(applied).contains("V1__create_pending_action.sql");
    }

    @Test
    void pendingActionIsUsableThroughTheMigratedSchema() {
        jdbcTemplate.update("insert into pending_action "
                        + "(id, action_type, payload, summary, status, idempotency_key, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SEND_INVOICE", "{}", "Invoice USD 250.00", "PROPOSED",
                "migration-check", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from pending_action where idempotency_key = ?",
                Integer.class, "migration-check");

        assertThat(rows).isEqualTo(1);
    }

    @Test
    void migratedSchemaEnforcesIdempotencyKeyUniqueness() {
        jdbcTemplate.update("insert into pending_action "
                        + "(id, action_type, payload, summary, status, idempotency_key, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SEND_INVOICE", "{}", "Invoice USD 250.00", "PROPOSED",
                "unique-check", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        assertThatThrownBy(() -> jdbcTemplate.update("insert into pending_action "
                        + "(id, action_type, payload, summary, status, idempotency_key, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SEND_INVOICE", "{}", "Invoice USD 250.00", "PROPOSED",
                "unique-check", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
