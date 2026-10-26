package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import org.springframework.stereotype.Component;

@Component
public class InvoiceReader {

    private final InvoiceClient invoiceClient;

    public InvoiceReader(InvoiceClient invoiceClient) {
        this.invoiceClient = invoiceClient;
    }

    public InvoiceSummary get(String invoiceId) {
        if (!PayPalIdentifiers.isInvoiceId(invoiceId)) {
            throw new IllegalArgumentException("Not a PayPal invoice id: " + invoiceId);
        }
        return invoiceClient.get(invoiceId);
    }

    public InvoiceList list(int page, int pageSize) {
        return invoiceClient.list(page, pageSize);
    }
}
