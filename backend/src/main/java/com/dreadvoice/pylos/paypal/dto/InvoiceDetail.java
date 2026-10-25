package com.dreadvoice.pylos.paypal.dto;

import java.time.LocalDate;

public record InvoiceDetail(
        String invoiceNumber,
        String currencyCode,
        LocalDate invoiceDate,
        String note
) {
}
