package com.dreadvoice.pylos.paypal.dto;

public record Money(
        String currencyCode,
        String value
) {
}
