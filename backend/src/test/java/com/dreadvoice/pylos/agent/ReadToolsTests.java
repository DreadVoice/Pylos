package com.dreadvoice.pylos.agent;

import com.dreadvoice.pylos.paypal.InvoiceReader;
import com.dreadvoice.pylos.paypal.dto.InvoiceDetail;
import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadToolsTests {

    private InvoiceReader invoiceReader;
    private ReadTools readTools;

    @BeforeEach
    void setUp() {
        invoiceReader = mock(InvoiceReader.class);
        readTools = new ReadTools(invoiceReader);
    }

    @Test
    void listsInvoicesOneLineEach() {
        when(invoiceReader.list(1, 10)).thenReturn(new InvoiceList(List.of(
                invoice("INV2-ONE", "0001", "SENT", "250.00"),
                invoice("INV2-TWO", "0002", "PAID", "100.00")), 2, 1));

        String listed = readTools.listRecentInvoices(10);

        assertThat(listed.lines()).hasSize(2);
        assertThat(listed).contains("INV2-ONE | number 0001 | SENT | USD 250.00");
        assertThat(listed).contains("INV2-TWO | number 0002 | PAID | USD 100.00");
    }

    @Test
    void reportsAnEmptyInvoiceListPlainly() {
        when(invoiceReader.list(1, 10)).thenReturn(new InvoiceList(List.of(), 0, 0));

        assertThat(readTools.listRecentInvoices(10)).isEqualTo("The seller has no invoices yet.");
    }

    @Test
    void capsThePageSizeItWillRequest() {
        when(invoiceReader.list(1, 20)).thenReturn(new InvoiceList(List.of(), 0, 0));

        readTools.listRecentInvoices(500);

        verify(invoiceReader).list(1, 20);
    }

    @Test
    void fallsBackToADefaultPageSize() {
        when(invoiceReader.list(1, 10)).thenReturn(new InvoiceList(List.of(), 0, 0));

        readTools.listRecentInvoices(0);

        verify(invoiceReader).list(1, 10);
    }

    @Test
    void getsASingleInvoice() {
        when(invoiceReader.get("INV2-7YQ5-K2GD-GDGY-962L"))
                .thenReturn(invoice("INV2-7YQ5-K2GD-GDGY-962L", "0007", "SENT", "250.00"));

        assertThat(readTools.getInvoice("INV2-7YQ5-K2GD-GDGY-962L"))
                .contains("INV2-7YQ5-K2GD-GDGY-962L | number 0007 | SENT | USD 250.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INV2-BAD", "' or 1=1 --", "../../invoices", "not-an-id"})
    void refusesMalformedInvoiceIdsWithoutCallingPayPal(String invoiceId) {
        when(invoiceReader.get(anyString()))
                .thenThrow(new IllegalArgumentException("Not a PayPal invoice id: " + invoiceId));

        assertThat(readTools.getInvoice(invoiceId)).contains("not a valid PayPal invoice id");
    }

    @Test
    void neverWritesAnythingThroughTheReader() {
        when(invoiceReader.list(anyInt(), anyInt())).thenReturn(new InvoiceList(List.of(), 0, 0));

        readTools.listRecentInvoices(5);

        verify(invoiceReader, never()).get(anyString());
    }

    private static InvoiceSummary invoice(String id, String number, String status, String amount) {
        return new InvoiceSummary(
                id,
                status,
                new InvoiceDetail(number, "USD", null, null),
                new Money("USD", amount),
                null,
                null,
                null);
    }
}
