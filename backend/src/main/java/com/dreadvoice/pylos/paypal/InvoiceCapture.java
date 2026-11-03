package com.dreadvoice.pylos.paypal;

import com.dreadvoice.pylos.paypal.dto.Money;

public record InvoiceCapture(
        String invoiceId,
        String captureId,
        String status,
        Money capturedAmount,
        Money refundedAmount
) {
}
