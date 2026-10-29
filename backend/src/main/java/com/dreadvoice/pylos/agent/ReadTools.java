package com.dreadvoice.pylos.agent;

import com.dreadvoice.pylos.paypal.InvoiceReader;
import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReadTools {

    private static final Logger log = LoggerFactory.getLogger(ReadTools.class);
    private static final int MAX_PAGE_SIZE = 20;

    private final InvoiceReader invoiceReader;

    public ReadTools(InvoiceReader invoiceReader) {
        this.invoiceReader = invoiceReader;
    }

    @Tool("List the seller's recent PayPal invoices, newest first. Returns one line per invoice "
            + "with its id, invoice number, status and amount.")
    public String listRecentInvoices(
            @P(value = "How many invoices to list, at most 20", required = false)
            int pageSize) {
        int effectivePageSize = pageSize <= 0 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        log.debug("Listing up to {} invoices", effectivePageSize);

        InvoiceList invoices = invoiceReader.list(1, effectivePageSize);
        List<InvoiceSummary> items = invoices.items();
        if (items.isEmpty()) {
            return "The seller has no invoices yet.";
        }
        return items.stream().map(ReadTools::describe).reduce((left, right) -> left + "\n" + right).orElseThrow();
    }

    @Tool("Get one PayPal invoice by its invoice id, for example INV2-7YQ5-K2GD-GDGY-962L.")
    public String getInvoice(
            @P("The PayPal invoice id, for example INV2-7YQ5-K2GD-GDGY-962L")
            String invoiceId) {
        try {
            return describe(invoiceReader.get(invoiceId));
        } catch (IllegalArgumentException exception) {
            return "That is not a valid PayPal invoice id, so nothing was looked up.";
        }
    }

    private static String describe(InvoiceSummary invoice) {
        String number = invoice.detail() == null ? "unknown" : invoice.detail().invoiceNumber();
        String amount = invoice.amount() == null
                ? "unknown amount"
                : invoice.amount().currencyCode() + " " + invoice.amount().value();
        String due = invoice.dueAmount() == null
                ? ""
                : ", due " + invoice.dueAmount().currencyCode() + " " + invoice.dueAmount().value();
        return invoice.id() + " | number " + number + " | " + invoice.status() + " | " + amount + due;
    }
}
