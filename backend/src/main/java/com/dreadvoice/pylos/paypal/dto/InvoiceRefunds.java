package com.dreadvoice.pylos.paypal.dto;

import java.util.List;

public record InvoiceRefunds(
        Money refundAmount,
        List<InvoiceRefundTransaction> transactions
) {

    public InvoiceRefunds {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
