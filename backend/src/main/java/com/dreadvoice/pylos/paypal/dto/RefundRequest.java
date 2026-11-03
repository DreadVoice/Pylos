package com.dreadvoice.pylos.paypal.dto;

public record RefundRequest(
        Money amount,
        String invoiceId,
        String noteToPayer
) {
}
