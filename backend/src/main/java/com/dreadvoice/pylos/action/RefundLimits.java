package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.action.payload.SendRefundPayload;
import com.dreadvoice.pylos.paypal.CaptureLookup;
import com.dreadvoice.pylos.paypal.InvoiceCapture;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RefundLimits {

    private static final Set<String> REFUNDABLE_STATUSES = Set.of("COMPLETED", "PARTIALLY_REFUNDED");

    private final CaptureLookup captureLookup;

    public RefundLimits(CaptureLookup captureLookup) {
        this.captureLookup = captureLookup;
    }

    public List<String> violations(SendRefundPayload payload) {
        Optional<BigDecimal> requested = MoneyAmounts.parse(payload.amount());
        if (requested.isEmpty() || requested.get().signum() <= 0) {
            return List.of();
        }

        Optional<InvoiceCapture> found = captureLookup.forInvoice(payload.invoiceId());
        if (found.isEmpty()) {
            return List.of("invoiceId: " + payload.invoiceId()
                    + " has no PayPal payment that can be refunded");
        }

        InvoiceCapture capture = found.get();
        if (!REFUNDABLE_STATUSES.contains(capture.status())) {
            return List.of("invoiceId: the payment for " + payload.invoiceId() + " is "
                    + capture.status() + " and cannot be refunded");
        }

        String capturedCurrency = capture.capturedAmount() == null
                ? null
                : capture.capturedAmount().currencyCode();
        if (capturedCurrency != null && !capturedCurrency.equalsIgnoreCase(payload.currencyCode())) {
            return List.of("currencyCode: must be " + capturedCurrency
                    + " to match the payment being refunded");
        }

        Optional<BigDecimal> captured = MoneyAmounts.parse(
                capture.capturedAmount() == null ? null : capture.capturedAmount().value());
        Optional<BigDecimal> refunded = MoneyAmounts.parse(
                capture.refundedAmount() == null ? null : capture.refundedAmount().value());
        if (captured.isEmpty()) {
            return List.of("invoiceId: PayPal reported no captured amount for " + payload.invoiceId());
        }

        BigDecimal remaining = captured.get().subtract(refunded.orElse(BigDecimal.ZERO));
        if (requested.get().compareTo(remaining) > 0) {
            return List.of("amount: must not exceed the " + remaining.toPlainString() + " "
                    + capturedCurrency + " still refundable on " + payload.invoiceId());
        }

        return List.of();
    }
}
