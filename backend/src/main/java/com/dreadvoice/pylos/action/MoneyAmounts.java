package com.dreadvoice.pylos.action;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MoneyAmounts {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^-?[0-9]{1,15}([.][0-9]{1,2})?$");

    private MoneyAmounts() {
    }

    public static Optional<BigDecimal> parse(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (!PLAIN_DECIMAL.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(trimmed));
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitAmount) {
        return quantity.multiply(unitAmount);
    }
}
