package com.dreadvoice.pylos.paypal;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class PayPalErrorRedactor {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}");
    private static final String EMAIL_PLACEHOLDER = "[redacted-email]";
    private static final int MAX_LENGTH = 1000;

    private PayPalErrorRedactor() {
    }

    public static String redact(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        String redacted = EMAIL.matcher(responseBody).replaceAll(EMAIL_PLACEHOLDER);
        if (redacted.length() > MAX_LENGTH) {
            return redacted.substring(0, MAX_LENGTH);
        }
        return redacted;
    }
}
