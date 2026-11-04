package com.dreadvoice.pylos.api;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(
        UUID id,
        String eventType,
        String summary,
        String invoiceId,
        String currencyCode,
        String amount,
        Instant occurredAt,
        boolean read
) {
}
