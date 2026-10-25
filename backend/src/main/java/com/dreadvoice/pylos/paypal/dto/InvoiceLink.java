package com.dreadvoice.pylos.paypal.dto;

public record InvoiceLink(
        String href,
        String rel,
        String method
) {
}
