package com.dreadvoice.pylos.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(
        name = "ux_notification_event_id", columnNames = "event_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "summary", nullable = false, length = 512)
    private String summary;

    @Column(name = "invoice_id", length = 128)
    private String invoiceId;

    @Column(name = "currency_code", length = 8)
    private String currencyCode;

    @Column(name = "amount", length = 32)
    private String amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String eventId,
                        String eventType,
                        String summary,
                        String invoiceId,
                        String currencyCode,
                        String amount,
                        Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.summary = summary;
        this.invoiceId = invoiceId;
        this.currencyCode = currencyCode;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSummary() {
        return summary;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getAmount() {
        return amount;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
