package com.dreadvoice.pylos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigCheckTests {

    private static final String VALID_URL = "jdbc:postgresql://127.0.0.1:5432/pylos";

    @Test
    void passesWithACompleteProdConfiguration() {
        assertThatCode(() -> new ProdConfigCheck(VALID_URL, "pylos", "secret", secured()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "postgres://pylos:secret@127.0.0.1:5432/pylos",
            "postgresql://127.0.0.1:5432/pylos",
            "jdbc:mysql://127.0.0.1:3306/pylos",
            "127.0.0.1:5432/pylos"
    })
    void refusesADatasourceUrlThatIsNotAPostgresJdbcUrl(String url) {
        assertThatThrownBy(() -> new ProdConfigCheck(url, "pylos", "secret", secured()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_URL must be a JDBC url")
                .hasMessageContaining("jdbc:postgresql:");
    }

    @Test
    void refusesAMissingDatasourceUrl() {
        assertThatThrownBy(() -> new ProdConfigCheck("", "pylos", "secret", secured()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_URL is not set");
    }

    @Test
    void refusesMissingDatabaseCredentials() {
        assertThatThrownBy(() -> new ProdConfigCheck(VALID_URL, "", "secret", secured()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_USERNAME is not set");

        assertThatThrownBy(() -> new ProdConfigCheck(VALID_URL, "pylos", "", secured()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_PASSWORD is not set");
    }

    @Test
    void refusesToDeployWithoutAnApiToken() {
        ApiSecurityProperties unsecured = new ApiSecurityProperties("", List.of("chrome-extension://*"));

        assertThatThrownBy(() -> new ProdConfigCheck(VALID_URL, "pylos", "secret", unsecured))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PYLOS_API_TOKEN is not set")
                .hasMessageContaining("never be deployed unauthenticated");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        ApiSecurityProperties unsecured = new ApiSecurityProperties(null, null);

        assertThatThrownBy(() -> new ProdConfigCheck("postgres://host/db", "", "", unsecured))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSTGRES_URL must be a JDBC url")
                .hasMessageContaining("POSTGRES_USERNAME is not set")
                .hasMessageContaining("POSTGRES_PASSWORD is not set")
                .hasMessageContaining("PYLOS_API_TOKEN is not set");
    }

    @Test
    void startsWithoutAllowedOriginsButWarns() {
        ApiSecurityProperties noOrigins = new ApiSecurityProperties("a-token", List.of());

        assertThatCode(() -> new ProdConfigCheck(VALID_URL, "pylos", "secret", noOrigins))
                .doesNotThrowAnyException();
    }

    private static ApiSecurityProperties secured() {
        return new ApiSecurityProperties("a-token", List.of("chrome-extension://*"));
    }
}
