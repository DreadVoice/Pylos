package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InvoiceReader {

    private static final String INVOICES_PATH = "/v2/invoicing/invoices";

    private final RestClient restClient;
    private final PayPalAuthClient authClient;

    public InvoiceReader(RestClient payPalRestClient, PayPalAuthClient authClient) {
        this.restClient = payPalRestClient;
        this.authClient = authClient;
    }

    public InvoiceSummary get(String invoiceId) {
        if (!PayPalIdentifiers.isInvoiceId(invoiceId)) {
            throw new IllegalArgumentException("Not a PayPal invoice id: " + invoiceId);
        }
        return PayPalRequests.withToken(authClient, "invoicing", token -> restClient.get()
                .uri(INVOICES_PATH + "/{invoiceId}", invoiceId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(InvoiceSummary.class));
    }

    public InvoiceList list(int page, int pageSize) {
        return PayPalRequests.withToken(authClient, "invoicing", token -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path(INVOICES_PATH)
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("total_required", true)
                        .build())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(InvoiceList.class));
    }
}
