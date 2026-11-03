package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.action.payload.SendRefundPayload;
import com.dreadvoice.pylos.paypal.CaptureLookup;
import com.dreadvoice.pylos.paypal.InvoiceCapture;
import com.dreadvoice.pylos.paypal.dto.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundLimitsTests {

    private static final String INVOICE_ID = "INV2-GS4Q-CBHK-S6QM-CC5V";
    private static final String CAPTURE_ID = "2S7263346H6677809";

    private CaptureLookup captureLookup;
    private RefundLimits refundLimits;

    @BeforeEach
    void setUp() {
        captureLookup = mock(CaptureLookup.class);
        refundLimits = new RefundLimits(captureLookup);
    }

    @Test
    void allowsARefundInsideTheCapturedAmount() {
        captured("COMPLETED", "60.00", "0.00");

        assertThat(refundLimits.violations(refundOf("15.00"))).isEmpty();
    }

    @Test
    void allowsARefundOfExactlyTheRemainingBalance() {
        captured("PARTIALLY_REFUNDED", "60.00", "45.00");

        assertThat(refundLimits.violations(refundOf("15.00"))).isEmpty();
    }

    @Test
    void rejectsARefundLargerThanTheCapturedAmount() {
        captured("COMPLETED", "60.00", "0.00");

        assertThat(refundLimits.violations(refundOf("50000.00")))
                .containsExactly("amount: must not exceed the 60.00 USD still refundable on " + INVOICE_ID);
    }

    @Test
    void rejectsARefundLargerThanWhatIsLeftAfterEarlierRefunds() {
        captured("PARTIALLY_REFUNDED", "60.00", "45.00");

        assertThat(refundLimits.violations(refundOf("15.01")))
                .containsExactly("amount: must not exceed the 15.00 USD still refundable on " + INVOICE_ID);
    }

    @Test
    void rejectsARefundOnAnInvoiceThatWasNeverPaid() {
        when(captureLookup.forInvoice(INVOICE_ID)).thenReturn(Optional.empty());

        assertThat(refundLimits.violations(refundOf("15.00")))
                .containsExactly("invoiceId: " + INVOICE_ID
                        + " has no PayPal payment that can be refunded");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "DECLINED", "REFUNDED", "FAILED"})
    void rejectsARefundAgainstACaptureThatIsNotRefundable(String status) {
        captured(status, "60.00", "0.00");

        assertThat(refundLimits.violations(refundOf("15.00")))
                .containsExactly("invoiceId: the payment for " + INVOICE_ID + " is " + status
                        + " and cannot be refunded");
    }

    @Test
    void rejectsARefundInADifferentCurrencyFromThePayment() {
        captured("COMPLETED", "60.00", "0.00");

        assertThat(refundLimits.violations(
                new SendRefundPayload(INVOICE_ID, "INR", "15.00", null)))
                .containsExactly("currencyCode: must be USD to match the payment being refunded");
    }

    @Test
    void rejectsARefundWhenPayPalReportsNoCapturedAmount() {
        when(captureLookup.forInvoice(INVOICE_ID)).thenReturn(Optional.of(
                new InvoiceCapture(INVOICE_ID, CAPTURE_ID, "COMPLETED", null, null)));

        assertThat(refundLimits.violations(refundOf("15.00")))
                .containsExactly("invoiceId: PayPal reported no captured amount for " + INVOICE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1.00", "abc", "1e3"})
    void leavesAmountsItCannotReadToTheShapeChecksAndNeverAsksPayPal(String amount) {
        assertThat(refundLimits.violations(refundOf(amount))).isEmpty();

        verifyNoInteractions(captureLookup);
    }

    @Test
    void treatsAMissingRefundTotalAsNothingRefundedYet() {
        when(captureLookup.forInvoice(INVOICE_ID)).thenReturn(Optional.of(new InvoiceCapture(
                INVOICE_ID, CAPTURE_ID, "COMPLETED", new Money("USD", "60.00"), null)));

        assertThat(refundLimits.violations(refundOf("60.00"))).isEmpty();
        assertThat(refundLimits.violations(refundOf("60.01"))).isNotEmpty();
    }

    @Test
    void acceptsACurrencyCodeThatOnlyDiffersInCase() {
        captured("COMPLETED", "60.00", "0.00");

        assertThat(refundLimits.violations(
                new SendRefundPayload(INVOICE_ID, "usd", "15.00", null))).isEmpty();
    }

    @Test
    void looksTheInvoiceUpByTheIdInThePayload() {
        when(captureLookup.forInvoice(anyString())).thenReturn(Optional.empty());

        refundLimits.violations(refundOf("15.00"));

        verify(captureLookup).forInvoice(INVOICE_ID);
    }

    private void captured(String status, String captured, String refunded) {
        when(captureLookup.forInvoice(INVOICE_ID)).thenReturn(Optional.of(new InvoiceCapture(
                INVOICE_ID,
                CAPTURE_ID,
                status,
                new Money("USD", captured),
                new Money("USD", refunded))));
    }

    private static SendRefundPayload refundOf(String amount) {
        return new SendRefundPayload(INVOICE_ID, "USD", amount, "Sorry about the damaged item");
    }
}
