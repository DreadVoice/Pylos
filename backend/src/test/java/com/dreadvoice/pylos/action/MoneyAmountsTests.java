package com.dreadvoice.pylos.action;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyAmountsTests {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "1", "250.00", "250.5", "250.50", "-5", "-0.01", "999999999999999"})
    void acceptsPlainDecimalsWithAtMostTwoPlaces(String value) {
        assertThat(MoneyAmounts.parse(value)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1e3",
            "1E3",
            "250.000",
            "250,00",
            "1_000",
            "abc",
            "$250.00",
            "250.00USD",
            "0x10",
            "Infinity",
            "NaN",
            "+250.00",
            ".50",
            "250.",
            "2 50",
            "1234567890123456"
    })
    void rejectsAnythingThatIsNotAPlainTwoPlaceDecimal(String value) {
        assertThat(MoneyAmounts.parse(value)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingValues(String value) {
        assertThat(MoneyAmounts.parse(value)).isEmpty();
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(MoneyAmounts.parse("  250.00  ")).contains(new BigDecimal("250.00"));
    }

    @Test
    void preservesScaleExactlyAsWritten() {
        assertThat(MoneyAmounts.parse("250.50").orElseThrow().scale()).isEqualTo(2);
        assertThat(MoneyAmounts.parse("250").orElseThrow().scale()).isZero();
    }

    @Test
    void multipliesLineTotalsWithoutBinaryFloatingPointError() {
        BigDecimal total = MoneyAmounts.lineTotal(
                MoneyAmounts.parse("3").orElseThrow(),
                MoneyAmounts.parse("0.10").orElseThrow());

        assertThat(total).isEqualByComparingTo("0.30");
        assertThat(total.add(MoneyAmounts.lineTotal(
                MoneyAmounts.parse("1").orElseThrow(),
                MoneyAmounts.parse("0.20").orElseThrow())))
                .isEqualByComparingTo("0.50");
    }
}
