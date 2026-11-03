package com.dreadvoice.pylos.paypal.dto;

import java.time.LocalDate;

public record InvoicePaymentTransaction(
        String paymentId,
        String type,
        String method,
        LocalDate paymentDate,
        Money amount
) {
}
