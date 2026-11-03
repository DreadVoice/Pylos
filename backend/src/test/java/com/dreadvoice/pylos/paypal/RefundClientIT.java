package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.CaptureDetail;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import com.dreadvoice.pylos.paypal.dto.RefundDetail;
import com.dreadvoice.pylos.paypal.dto.RefundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class RefundClientIT {

    private static final String SMALLEST_REFUND = "0.01";
    private static final Set<String> PAID_STATUSES =
            Set.of("PAID", "PARTIALLY_PAID", "PARTIALLY_REFUNDED", "MARKED_AS_PAID");

    @Autowired
    private InvoiceClient invoiceClient;

    @Autowired
    private RefundClient refundClient;

    @Autowired
    private CaptureReader captureReader;

    @Autowired
    private CaptureLookup captureLookup;

    @Test
    void readsTheCaptureBehindAPaidSandboxInvoice() {
        InvoiceCapture capture = firstPaidInvoiceCapture()
                .orElseThrow(() -> new AssertionError("unreachable"));

        CaptureDetail detail = captureReader.get(capture.captureId());

        assertThat(detail.id()).isEqualTo(capture.captureId());
        assertThat(detail.status()).isNotBlank();
        assertThat(detail.amount().value()).isEqualTo(capture.capturedAmount().value());
    }

    @Test
    void refundsTheSmallestAmountAgainstAPaidSandboxInvoice() {
        InvoiceCapture capture = firstPaidInvoiceCapture()
                .orElseThrow(() -> new AssertionError("unreachable"));
        assumeTrue("COMPLETED".equals(capture.status()) || "PARTIALLY_REFUNDED".equals(capture.status()),
                "capture " + capture.captureId() + " is " + capture.status() + ", not refundable");
        assumeTrue(remaining(capture).compareTo(new BigDecimal(SMALLEST_REFUND)) >= 0,
                "capture " + capture.captureId() + " is fully refunded");

        String currency = capture.capturedAmount().currencyCode();
        RefundDetail refund = refundClient.refund(
                capture.captureId(),
                new RefundRequest(new Money(currency, SMALLEST_REFUND), capture.invoiceId(),
                        "Pylos sandbox verification"),
                "it-refund-" + UUID.randomUUID());

        assertThat(refund.id()).isNotBlank();
        assertThat(refund.status()).isIn("COMPLETED", "PENDING");
        assertThat(refund.amount().value()).isEqualTo(SMALLEST_REFUND);

        InvoiceCapture after = captureLookup.forInvoice(capture.invoiceId()).orElseThrow();
        assertThat(new BigDecimal(after.refundedAmount().value()))
                .isGreaterThanOrEqualTo(new BigDecimal(capture.refundedAmount().value()));
    }

    private Optional<InvoiceCapture> firstPaidInvoiceCapture() {
        Optional<InvoiceCapture> capture = invoiceClient.list(1, 40).items().stream()
                .filter(RefundClientIT::looksPaid)
                .map(InvoiceSummary::id)
                .flatMap(invoiceId -> captureLookup.forInvoice(invoiceId).stream())
                .findFirst();

        assumeTrue(capture.isPresent(),
                "no paid sandbox invoice to refund; pay one at sandbox.paypal.com and rerun");
        return capture;
    }

    private static boolean looksPaid(InvoiceSummary invoice) {
        return PAID_STATUSES.contains(invoice.status());
    }

    private static BigDecimal remaining(InvoiceCapture capture) {
        return new BigDecimal(capture.capturedAmount().value())
                .subtract(new BigDecimal(capture.refundedAmount().value()));
    }
}
