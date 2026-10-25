package com.dreadvoice.pylos.paypal.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class InvoiceDtoJsonTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesDraftRequestWithSnakeCaseFields() throws Exception {
        InvoiceDraftRequest request = new InvoiceDraftRequest(
                new InvoiceDetail("INV-0001", "USD", LocalDate.of(2026, 10, 25), "Thanks for your order"),
                new Invoicer(new InvoiceName("Pylos", "Seller"), "seller@business.example.com"),
                List.of(new InvoiceRecipient(
                        new BillingInfo(new InvoiceName("Test", "Buyer"), "buyer@personal.example.com"))),
                List.of(new InvoiceItem("Logo design", "Final artwork", "1", new Money("USD", "250.00")))
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"invoice_number\":\"INV-0001\"");
        assertThat(json).contains("\"currency_code\":\"USD\"");
        assertThat(json).contains("\"invoice_date\":\"2026-10-25\"");
        assertThat(json).contains("\"primary_recipients\"");
        assertThat(json).contains("\"billing_info\"");
        assertThat(json).contains("\"email_address\":\"buyer@personal.example.com\"");
        assertThat(json).contains("\"unit_amount\":{\"currency_code\":\"USD\",\"value\":\"250.00\"}");
        assertThat(json).contains("\"given_name\":\"Pylos\"");
    }

    @Test
    void omitsNullFieldsFromDraftRequest() throws Exception {
        InvoiceDraftRequest request = new InvoiceDraftRequest(
                new InvoiceDetail(null, "USD", null, null),
                null,
                List.of(),
                List.of()
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).doesNotContain("invoice_number");
        assertThat(json).doesNotContain("invoice_date");
        assertThat(json).doesNotContain("note");
        assertThat(json).doesNotContain("invoicer");
        assertThat(json).contains("\"currency_code\":\"USD\"");
    }

    @Test
    void deserializesInvoiceSummaryIgnoringUnknownFields() throws Exception {
        String json = """
                {
                  "id": "INV2-ABCD-1234-EFGH-5678",
                  "status": "SENT",
                  "detail": {
                    "invoice_number": "INV-0001",
                    "currency_code": "USD",
                    "invoice_date": "2026-10-25",
                    "note": "Thanks for your order",
                    "metadata": { "create_time": "2026-10-25T10:00:00Z" }
                  },
                  "amount": {
                    "currency_code": "USD",
                    "value": "250.00",
                    "breakdown": { "item_total": { "currency_code": "USD", "value": "250.00" } }
                  },
                  "due_amount": { "currency_code": "USD", "value": "250.00" },
                  "links": [
                    { "href": "https://api-m.sandbox.paypal.com/v2/invoicing/invoices/INV2-ABCD", "rel": "self", "method": "GET" }
                  ]
                }
                """;

        InvoiceSummary summary = objectMapper.readValue(json, InvoiceSummary.class);

        assertThat(summary.id()).isEqualTo("INV2-ABCD-1234-EFGH-5678");
        assertThat(summary.status()).isEqualTo("SENT");
        assertThat(summary.detail().invoiceNumber()).isEqualTo("INV-0001");
        assertThat(summary.detail().invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(summary.amount().value()).isEqualTo("250.00");
        assertThat(summary.dueAmount().currencyCode()).isEqualTo("USD");
        assertThat(summary.links()).hasSize(1);
        assertThat(summary.links().getFirst().rel()).isEqualTo("self");
    }

    @Test
    void defaultsMissingInvoiceListItemsToEmptyList() throws Exception {
        String json = """
                { "total_items": 0, "total_pages": 0, "links": [] }
                """;

        InvoiceList list = objectMapper.readValue(json, InvoiceList.class);

        assertThat(list.items()).isEmpty();
        assertThat(list.totalItems()).isZero();
    }

    @Test
    void deserializesInvoiceList() throws Exception {
        String json = """
                {
                  "total_items": 2,
                  "total_pages": 1,
                  "items": [
                    { "id": "INV2-ONE", "status": "DRAFT" },
                    { "id": "INV2-TWO", "status": "PAID" }
                  ],
                  "links": []
                }
                """;

        InvoiceList list = objectMapper.readValue(json, InvoiceList.class);

        assertThat(list.totalItems()).isEqualTo(2);
        assertThat(list.totalPages()).isEqualTo(1);
        assertThat(list.items()).hasSize(2);
        assertThat(list.items().getFirst().id()).isEqualTo("INV2-ONE");
        assertThat(list.items().getLast().status()).isEqualTo("PAID");
    }
}
