package com.dreadvoice.pylos.paypal.dto;

public record RefundDetail(
        String id,
        String status,
        Money amount,
        String noteToPayer
) {
}
