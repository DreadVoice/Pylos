package com.dreadvoice.pylos.paypal.dto;

import java.util.List;

public record InvoiceSummary(
        String id,
        String status,
        InvoiceDetail detail,
        Money amount,
        Money dueAmount,
        List<InvoiceRecipient> primaryRecipients,
        List<InvoiceLink> links
) {
}
