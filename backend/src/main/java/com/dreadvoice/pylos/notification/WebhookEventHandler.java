package com.dreadvoice.pylos.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventHandler.class);

    private static final int MAX_SUMMARY = 512;
    private static final int MAX_ID = 128;

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookEventHandler(NotificationRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<Notification> handle(String rawBody) {
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception unreadable) {
            log.warn("Ignoring a webhook whose body could not be parsed as JSON");
            return Optional.empty();
        }

        String eventId = truncate(text(event, "id"), MAX_ID);
        String eventType = text(event, "event_type");
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventType)) {
            log.warn("Ignoring a webhook with no event id or type");
            return Optional.empty();
        }
        if (repository.existsByEventId(eventId)) {
            log.info("Ignoring webhook {} because it was already recorded", eventId);
            return Optional.empty();
        }

        JsonNode resource = event.path("resource");
        JsonNode invoice = resource.has("invoice") ? resource.path("invoice") : resource;

        return switch (eventType) {
            case "INVOICING.INVOICE.PAID" -> record(eventId, eventType, event,
                    invoiceId(invoice), amountNode(invoice), "Invoice %s was paid"
                            .formatted(invoiceLabel(invoice)));
            case "INVOICING.INVOICE.REFUNDED" -> record(eventId, eventType, event,
                    invoiceId(invoice), amountNode(invoice), "Invoice %s was refunded"
                            .formatted(invoiceLabel(invoice)));
            case "INVOICING.INVOICE.CANCELLED" -> record(eventId, eventType, event,
                    invoiceId(invoice), amountNode(invoice), "Invoice %s was cancelled"
                            .formatted(invoiceLabel(invoice)));
            case "PAYMENT.CAPTURE.COMPLETED" -> record(eventId, eventType, event,
                    text(resource, "invoice_id"), resource.path("amount"),
                    "A payment of %s completed".formatted(describe(resource.path("amount"))));
            case "PAYMENT.CAPTURE.REFUNDED" -> record(eventId, eventType, event,
                    text(resource, "invoice_id"), resource.path("amount"),
                    "A refund of %s completed".formatted(describe(resource.path("amount"))));
            default -> {
                log.debug("Ignoring webhook event type {}", eventType);
                yield Optional.empty();
            }
        };
    }

    private Optional<Notification> record(String eventId,
                                          String eventType,
                                          JsonNode event,
                                          String invoiceId,
                                          JsonNode amount,
                                          String summary) {
        Notification notification = new Notification(
                eventId,
                truncate(eventType, 64),
                truncate(summary, MAX_SUMMARY),
                truncate(invoiceId, MAX_ID),
                truncate(currencyOf(amount), 8),
                truncate(valueOf(amount), 32),
                occurredAt(event));

        try {
            return Optional.of(repository.saveAndFlush(notification));
        } catch (DataIntegrityViolationException alreadyRecorded) {
            log.info("Webhook {} was recorded by another delivery", eventId);
            return Optional.empty();
        }
    }

    private Instant occurredAt(JsonNode event) {
        String createTime = text(event, "create_time");
        if (StringUtils.hasText(createTime)) {
            try {
                return Instant.parse(createTime);
            } catch (DateTimeParseException unparseable) {
                log.debug("Webhook create_time {} is not an instant", createTime);
            }
        }
        return clock.instant();
    }

    private String invoiceId(JsonNode invoice) {
        return text(invoice, "id");
    }

    private JsonNode amountNode(JsonNode invoice) {
        JsonNode amount = invoice.path("amount");
        return amount.isObject() ? amount : invoice.path("due_amount");
    }

    private String invoiceLabel(JsonNode invoice) {
        String number = text(invoice.path("detail"), "invoice_number");
        return StringUtils.hasText(number) ? number : String.valueOf(invoiceId(invoice));
    }

    private String describe(JsonNode amount) {
        String currency = currencyOf(amount);
        String value = valueOf(amount);
        if (!StringUtils.hasText(value)) {
            return "an unknown amount";
        }
        return StringUtils.hasText(currency) ? currency + " " + value : value;
    }

    private static String currencyOf(JsonNode amount) {
        String currencyCode = text(amount, "currency_code");
        return currencyCode != null ? currencyCode : text(amount, "currency");
    }

    private static String valueOf(JsonNode amount) {
        String value = text(amount, "value");
        return value != null ? value : text(amount, "total");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
