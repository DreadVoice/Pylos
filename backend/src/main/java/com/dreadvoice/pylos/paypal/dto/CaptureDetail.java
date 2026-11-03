package com.dreadvoice.pylos.paypal.dto;

public record CaptureDetail(
        String id,
        String status,
        Money amount,
        String invoiceId
) {
}
