package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.CaptureDetail;
import com.dreadvoice.pylos.paypal.dto.InvoicePaymentTransaction;
import com.dreadvoice.pylos.paypal.dto.InvoicePayments;
import com.dreadvoice.pylos.paypal.dto.InvoiceRefundTransaction;
import com.dreadvoice.pylos.paypal.dto.InvoiceRefunds;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureLookupTests {

    private static final String INVOICE_ID = "INV2-GS4Q-CBHK-S6QM-CC5V";
    private static final String CAPTURE_ID = "3C679366HH908993F";

    private InvoiceReader invoiceReader;
    private CaptureReader captureReader;
    private CaptureLookup captureLookup;

    @BeforeEach
    void setUp() {
        invoiceReader = mock(InvoiceReader.class);
        captureReader = mock(CaptureReader.class);
        captureLookup = new CaptureLookup(invoiceReader, captureReader);
    }

    @Test
    void findsTheCaptureBehindAPaidInvoice() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(paid(CAPTURE_ID, "60.00", null));
        when(captureReader.get(CAPTURE_ID))
                .thenReturn(new CaptureDetail(CAPTURE_ID, "COMPLETED", new Money("USD", "60.00"), INVOICE_ID));

        InvoiceCapture capture = captureLookup.forInvoice(INVOICE_ID).orElseThrow();

        assertThat(capture.invoiceId()).isEqualTo(INVOICE_ID);
        assertThat(capture.captureId()).isEqualTo(CAPTURE_ID);
        assertThat(capture.status()).isEqualTo("COMPLETED");
        assertThat(capture.capturedAmount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(capture.refundedAmount()).isEqualTo(new Money("USD", "0.00"));
    }

    @Test
    void reportsWhatHasAlreadyBeenRefunded() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(paid(CAPTURE_ID, "60.00", "15.00"));
        when(captureReader.get(CAPTURE_ID))
                .thenReturn(new CaptureDetail(CAPTURE_ID, "PARTIALLY_REFUNDED",
                        new Money("USD", "60.00"), INVOICE_ID));

        InvoiceCapture capture = captureLookup.forInvoice(INVOICE_ID).orElseThrow();

        assertThat(capture.status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(capture.capturedAmount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(capture.refundedAmount()).isEqualTo(new Money("USD", "15.00"));
    }

    @Test
    void findsNoCaptureForAnUnpaidInvoice() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(unpaid());

        assertThat(captureLookup.forInvoice(INVOICE_ID)).isEmpty();

        verify(captureReader, never()).get(anyString());
    }

    @Test
    void findsNoCaptureWhenTheInvoiceCarriesNoPaymentsBlock() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(new InvoiceSummary(
                INVOICE_ID, "SENT", null, new Money("USD", "60.00"), null, null, null, null, null));

        assertThat(captureLookup.forInvoice(INVOICE_ID)).isEmpty();

        verify(captureReader, never()).get(anyString());
    }

    @Test
    void ignoresPaymentsThatAreNotShapedLikeACaptureId() {
        InvoiceSummary invoice = new InvoiceSummary(
                INVOICE_ID, "PAID", null, new Money("USD", "60.00"), null, null, null,
                new InvoicePayments(new Money("USD", "60.00"), List.of(
                        new InvoicePaymentTransaction("EXTERNAL-RECORD", "EXTERNAL", "CASH",
                                LocalDate.of(2026, 11, 1), new Money("USD", "20.00")),
                        new InvoicePaymentTransaction(CAPTURE_ID, "PAYPAL", "PAYPAL",
                                LocalDate.of(2026, 11, 1), new Money("USD", "40.00")))),
                null);
        when(invoiceReader.get(INVOICE_ID)).thenReturn(invoice);
        when(captureReader.get(CAPTURE_ID))
                .thenReturn(new CaptureDetail(CAPTURE_ID, "COMPLETED", new Money("USD", "40.00"), INVOICE_ID));

        InvoiceCapture capture = captureLookup.forInvoice(INVOICE_ID).orElseThrow();

        assertThat(capture.captureId()).isEqualTo(CAPTURE_ID);
        assertThat(capture.capturedAmount()).isEqualTo(new Money("USD", "40.00"));
    }

    @Test
    void fallsBackToThePaidAmountWhenPayPalReturnsNoCaptureAmount() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(paid(CAPTURE_ID, "60.00", null));
        when(captureReader.get(CAPTURE_ID))
                .thenReturn(new CaptureDetail(CAPTURE_ID, "COMPLETED", null, INVOICE_ID));

        InvoiceCapture capture = captureLookup.forInvoice(INVOICE_ID).orElseThrow();

        assertThat(capture.capturedAmount()).isEqualTo(new Money("USD", "60.00"));
        assertThat(capture.refundedAmount()).isEqualTo(new Money("USD", "0.00"));
    }

    @Test
    void returnsEmptyWhenTheInvoiceCannotBeRead() {
        when(invoiceReader.get(INVOICE_ID)).thenReturn(null);

        assertThat(captureLookup.forInvoice(INVOICE_ID)).isEqualTo(Optional.empty());
    }

    private static InvoiceSummary paid(String captureId, String captured, String refunded) {
        InvoiceRefunds refunds = refunded == null ? null : new InvoiceRefunds(
                new Money("USD", refunded),
                List.of(new InvoiceRefundTransaction(null, "2W750046D1165843U", "PAYPAL",
                        LocalDate.of(2026, 11, 2), new Money("USD", refunded))));

        return new InvoiceSummary(
                INVOICE_ID,
                "PAID",
                null,
                new Money("USD", captured),
                new Money("USD", "0.00"),
                null,
                null,
                new InvoicePayments(new Money("USD", captured), List.of(
                        new InvoicePaymentTransaction(captureId, "PAYPAL", "PAYPAL",
                                LocalDate.of(2026, 11, 1), new Money("USD", captured)))),
                refunds);
    }

    private static InvoiceSummary unpaid() {
        return new InvoiceSummary(
                INVOICE_ID,
                "SENT",
                null,
                new Money("USD", "60.00"),
                new Money("USD", "60.00"),
                null,
                null,
                new InvoicePayments(null, null),
                null);
    }
}
