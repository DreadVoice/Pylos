package com.dreadvoice.pylos.paypal.dto;

import java.time.LocalDate;

public record InvoiceRefundTransaction(
        String refundId,
        String recipientTransactionId,
        String type,
        LocalDate refundDate,
        Money amount
) {
}
