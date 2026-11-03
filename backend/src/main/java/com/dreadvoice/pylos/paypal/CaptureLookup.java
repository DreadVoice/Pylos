package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.CaptureDetail;
import com.dreadvoice.pylos.paypal.dto.InvoicePaymentTransaction;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CaptureLookup {

    private static final String ZERO = "0.00";

    private final InvoiceReader invoiceReader;
    private final CaptureReader captureReader;

    public CaptureLookup(InvoiceReader invoiceReader, CaptureReader captureReader) {
        this.invoiceReader = invoiceReader;
        this.captureReader = captureReader;
    }

    public Optional<InvoiceCapture> forInvoice(String invoiceId) {
        InvoiceSummary invoice = invoiceReader.get(invoiceId);
        if (invoice == null) {
            return Optional.empty();
        }
        return captureId(invoice)
                .map(captureId -> toInvoiceCapture(invoice, captureId, captureReader.get(captureId)));
    }

    private InvoiceCapture toInvoiceCapture(InvoiceSummary invoice, String captureId, CaptureDetail capture) {
        Money captured = capture != null && capture.amount() != null
                ? capture.amount()
                : paidAmount(invoice);
        return new InvoiceCapture(
                invoice.id(),
                captureId,
                capture == null ? null : capture.status(),
                captured,
                refundedAmount(invoice, captured));
    }

    private Optional<String> captureId(InvoiceSummary invoice) {
        if (invoice.payments() == null) {
            return Optional.empty();
        }
        return invoice.payments().transactions().stream()
                .map(InvoicePaymentTransaction::paymentId)
                .filter(PayPalIdentifiers::isCaptureId)
                .findFirst();
    }

    private Money paidAmount(InvoiceSummary invoice) {
        return invoice.payments() == null ? null : invoice.payments().paidAmount();
    }

    private Money refundedAmount(InvoiceSummary invoice, Money captured) {
        if (invoice.refunds() != null && invoice.refunds().refundAmount() != null) {
            return invoice.refunds().refundAmount();
        }
        return new Money(captured == null ? null : captured.currencyCode(), ZERO);
    }
}
