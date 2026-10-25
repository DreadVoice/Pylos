package com.dreadvoice.pylos.paypal.dto;

import java.util.List;

public record InvoiceDraftRequest(
        InvoiceDetail detail,
        Invoicer invoicer,
        List<InvoiceRecipient> primaryRecipients,
        List<InvoiceItem> items
) {
}
