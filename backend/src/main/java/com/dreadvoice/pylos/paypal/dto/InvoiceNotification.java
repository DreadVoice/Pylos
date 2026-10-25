package com.dreadvoice.pylos.paypal.dto;

public record InvoiceNotification(
        String subject,
        String note,
        Boolean sendToInvoicer,
        Boolean sendToRecipient
) {
}
