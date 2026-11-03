package com.dreadvoice.pylos.paypal.dto;

import java.util.List;

public record InvoicePayments(
        Money paidAmount,
        List<InvoicePaymentTransaction> transactions
) {

    public InvoicePayments {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
