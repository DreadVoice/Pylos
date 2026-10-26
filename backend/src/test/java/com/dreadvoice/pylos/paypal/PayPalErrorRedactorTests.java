package com.dreadvoice.pylos.paypal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalErrorRedactorTests {

    @Test
    void replacesEmailAddressesWithAPlaceholder() {
        String redacted = PayPalErrorRedactor.redact(
                "{\"name\":\"VALIDATION_ERROR\",\"value\":\"buyer@personal.example.com\"}");

        assertThat(redacted).contains("VALIDATION_ERROR");
        assertThat(redacted).contains("[redacted-email]");
        assertThat(redacted).doesNotContain("buyer@personal.example.com");
    }

    @Test
    void replacesEveryEmailAddressInTheBody() {
        String redacted = PayPalErrorRedactor.redact(
                "buyer@personal.example.com and seller@business.example.com both failed");

        assertThat(redacted).isEqualTo("[redacted-email] and [redacted-email] both failed");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void returnsNullForMissingBodies(String body) {
        assertThat(PayPalErrorRedactor.redact(body)).isNull();
    }

    @Test
    void leavesBodiesWithoutEmailsUnchanged() {
        assertThat(PayPalErrorRedactor.redact("{\"name\":\"RATE_LIMIT_REACHED\"}"))
                .isEqualTo("{\"name\":\"RATE_LIMIT_REACHED\"}");
    }

    @Test
    void capsVeryLongBodies() {
        String body = "x".repeat(5000);

        assertThat(PayPalErrorRedactor.redact(body)).hasSize(1000);
    }
}
