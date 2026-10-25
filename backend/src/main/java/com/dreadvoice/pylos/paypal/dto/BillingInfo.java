package com.dreadvoice.pylos.paypal.dto;

public record BillingInfo(
        InvoiceName name,
        String emailAddress
) {
}
