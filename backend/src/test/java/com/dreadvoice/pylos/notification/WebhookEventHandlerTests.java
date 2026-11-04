package com.dreadvoice.pylos.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class WebhookEventHandlerTests {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Autowired
    private NotificationRepository repository;

    private WebhookEventHandler handler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        handler = new WebhookEventHandler(repository, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsAPaidInvoice() {
        Optional<Notification> recorded = handler.handle("""
                {
                  "id": "WH-INV-PAID-1",
                  "event_type": "INVOICING.INVOICE.PAID",
                  "create_time": "2026-11-04T09:15:00Z",
                  "resource": {
                    "invoice": {
                      "id": "INV2-GS4Q-CBHK-S6QM-CC5V",
                      "status": "PAID",
                      "detail": { "invoice_number": "0058", "currency_code": "USD" },
                      "amount": { "currency_code": "USD", "value": "60.00" }
                    }
                  }
                }
                """);

        Notification notification = recorded.orElseThrow();
        assertThat(notification.getEventId()).isEqualTo("WH-INV-PAID-1");
        assertThat(notification.getEventType()).isEqualTo("INVOICING.INVOICE.PAID");
        assertThat(notification.getSummary()).isEqualTo("Invoice 0058 was paid");
        assertThat(notification.getInvoiceId()).isEqualTo("INV2-GS4Q-CBHK-S6QM-CC5V");
        assertThat(notification.getCurrencyCode()).isEqualTo("USD");
        assertThat(notification.getAmount()).isEqualTo("60.00");
        assertThat(notification.getOccurredAt()).isEqualTo(Instant.parse("2026-11-04T09:15:00Z"));
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void readsAnInvoiceThatIsNotWrappedInAnInvoiceField() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-INV-PAID-2",
                  "event_type": "INVOICING.INVOICE.PAID",
                  "resource": {
                    "id": "INV2-GS4Q-CBHK-S6QM-CC5V",
                    "detail": { "invoice_number": "0059" },
                    "amount": { "currency_code": "USD", "value": "25.00" }
                  }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("Invoice 0059 was paid");
        assertThat(notification.getAmount()).isEqualTo("25.00");
    }

    @Test
    void fallsBackToTheInvoiceIdWhenThereIsNoInvoiceNumber() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-INV-PAID-3",
                  "event_type": "INVOICING.INVOICE.PAID",
                  "resource": { "invoice": { "id": "INV2-GS4Q-CBHK-S6QM-CC5V" } }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("Invoice INV2-GS4Q-CBHK-S6QM-CC5V was paid");
    }

    @Test
    void recordsACompletedRefund() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-REFUND-1",
                  "event_type": "PAYMENT.CAPTURE.REFUNDED",
                  "create_time": "2026-11-04T10:00:00Z",
                  "resource": {
                    "id": "1JU08902781691411",
                    "status": "COMPLETED",
                    "invoice_id": "INV2-GS4Q-CBHK-S6QM-CC5V",
                    "amount": { "currency_code": "USD", "value": "30.00" }
                  }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("A refund of USD 30.00 completed");
        assertThat(notification.getInvoiceId()).isEqualTo("INV2-GS4Q-CBHK-S6QM-CC5V");
        assertThat(notification.getAmount()).isEqualTo("30.00");
    }

    @Test
    void recordsACompletedPayment() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-CAPTURE-1",
                  "event_type": "PAYMENT.CAPTURE.COMPLETED",
                  "resource": { "amount": { "currency_code": "USD", "value": "60.00" } }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("A payment of USD 60.00 completed");
        assertThat(notification.getInvoiceId()).isNull();
    }

    @Test
    void recordsACancelledInvoice() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-CANCEL-1",
                  "event_type": "INVOICING.INVOICE.CANCELLED",
                  "resource": { "invoice": { "id": "INV2-A", "detail": { "invoice_number": "0060" } } }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("Invoice 0060 was cancelled");
    }

    @Test
    void recordsADeliveryOnlyOnceHoweverOftenPayPalRetries() {
        String event = """
                {
                  "id": "WH-REPLAY-1",
                  "event_type": "INVOICING.INVOICE.PAID",
                  "resource": { "invoice": { "id": "INV2-A", "detail": { "invoice_number": "0061" } } }
                }
                """;

        assertThat(handler.handle(event)).isPresent();
        assertThat(handler.handle(event)).isEmpty();
        assertThat(handler.handle(event)).isEmpty();

        assertThat(repository.findTop50ByOrderByOccurredAtDesc()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CHECKOUT.ORDER.APPROVED",
            "BILLING.SUBSCRIPTION.CREATED",
            "CUSTOMER.DISPUTE.CREATED"
    })
    void ignoresEventTypesItDoesNotUnderstand(String eventType) {
        Optional<Notification> recorded = handler.handle(
                "{\"id\":\"WH-OTHER-1\",\"event_type\":\"" + eventType + "\",\"resource\":{}}");

        assertThat(recorded).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json at all",
            "{}",
            "{\"id\":\"WH-1\"}",
            "{\"event_type\":\"INVOICING.INVOICE.PAID\"}",
            "[]"
    })
    void ignoresBodiesItCannotMakeSenseOf(String body) {
        assertThat(handler.handle(body)).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void fallsBackToTheClockWhenPayPalSendsNoUsableCreateTime() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-NO-TIME-1",
                  "event_type": "INVOICING.INVOICE.PAID",
                  "create_time": "not a timestamp",
                  "resource": { "invoice": { "id": "INV2-A" } }
                }
                """).orElseThrow();

        assertThat(notification.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void readsTheOlderAmountSpellingPayPalStillSends() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-REFUND-V1",
                  "event_type": "PAYMENT.CAPTURE.REFUNDED",
                  "resource": {
                    "id": "8VC964970N548345V",
                    "state": "completed",
                    "amount": { "total": "-0.15", "currency": "USD" }
                  }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("A refund of USD -0.15 completed");
        assertThat(notification.getCurrencyCode()).isEqualTo("USD");
        assertThat(notification.getAmount()).isEqualTo("-0.15");
    }

    @Test
    void prefersTheCurrentSpellingWhenBothArePresent() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-REFUND-BOTH",
                  "event_type": "PAYMENT.CAPTURE.REFUNDED",
                  "resource": {
                    "amount": { "currency_code": "USD", "value": "30.00",
                                "currency": "INR", "total": "999.00" }
                  }
                }
                """).orElseThrow();

        assertThat(notification.getCurrencyCode()).isEqualTo("USD");
        assertThat(notification.getAmount()).isEqualTo("30.00");
    }

    @Test
    void describesARefundWithNoAmountWithoutInventingOne() {
        Notification notification = handler.handle("""
                {
                  "id": "WH-REFUND-2",
                  "event_type": "PAYMENT.CAPTURE.REFUNDED",
                  "resource": { "id": "1JU08902781691411" }
                }
                """).orElseThrow();

        assertThat(notification.getSummary()).isEqualTo("A refund of an unknown amount completed");
        assertThat(notification.getAmount()).isNull();
        assertThat(notification.getCurrencyCode()).isNull();
    }
}
