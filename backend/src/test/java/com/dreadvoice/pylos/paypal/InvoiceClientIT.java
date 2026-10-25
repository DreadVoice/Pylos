package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.BillingInfo;
import com.dreadvoice.pylos.paypal.dto.InvoiceDetail;
import com.dreadvoice.pylos.paypal.dto.InvoiceDraftRequest;
import com.dreadvoice.pylos.paypal.dto.InvoiceItem;
import com.dreadvoice.pylos.paypal.dto.InvoiceList;
import com.dreadvoice.pylos.paypal.dto.InvoiceName;
import com.dreadvoice.pylos.paypal.dto.InvoiceNotification;
import com.dreadvoice.pylos.paypal.dto.InvoiceRecipient;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class InvoiceClientIT {

    @Autowired
    private InvoiceClient invoiceClient;

    @Value("${SANDBOX_BUYER_EMAIL:}")
    private String buyerEmail;

    @BeforeEach
    void requireSandboxBuyer() {
        assumeTrue(StringUtils.hasText(buyerEmail), "SANDBOX_BUYER_EMAIL is not configured");
    }

    @Test
    void createsAndSendsSandboxInvoice() {
        String invoiceNumber = invoiceClient.generateNextInvoiceNumber();
        assertThat(invoiceNumber).isNotBlank();

        InvoiceDraftRequest request = new InvoiceDraftRequest(
                new InvoiceDetail(invoiceNumber, "USD", LocalDate.now(), "Pylos sandbox verification"),
                null,
                List.of(new InvoiceRecipient(
                        new BillingInfo(new InvoiceName("Test", "Buyer"), buyerEmail))),
                List.of(new InvoiceItem("Logo design", "Final artwork", "1", new Money("USD", "250.00")))
        );

        InvoiceSummary draft = invoiceClient.createDraft(request);
        assertThat(draft.id()).isNotBlank();
        assertThat(draft.status()).isEqualTo("DRAFT");

        invoiceClient.send(draft.id(), new InvoiceNotification(
                "Invoice from Pylos", "Thanks for your order", false, true));

        InvoiceSummary sent = invoiceClient.get(draft.id());
        assertThat(sent.status()).isIn("SENT", "UNPAID", "PAYMENT_PENDING");
        assertThat(sent.amount().value()).isEqualTo("250.00");
    }

    @Test
    void listsInvoicesForTheMerchant() {
        InvoiceList invoices = invoiceClient.list(1, 10);

        assertThat(invoices.items()).isNotNull();
        assertThat(invoices.totalItems()).isNotNull();
    }
}
