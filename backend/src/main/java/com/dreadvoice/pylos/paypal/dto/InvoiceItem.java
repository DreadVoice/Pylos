package com.dreadvoice.pylos.paypal.dto;

public record InvoiceItem(
        String name,
        String description,
        String quantity,
        Money unitAmount
) {
}
