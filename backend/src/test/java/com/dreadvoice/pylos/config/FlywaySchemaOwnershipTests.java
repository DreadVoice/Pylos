package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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
}
