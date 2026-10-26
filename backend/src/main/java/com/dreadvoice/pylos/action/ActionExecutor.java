package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.action.payload.SendInvoicePayload;
import com.dreadvoice.pylos.action.payload.SendReminderPayload;
import com.dreadvoice.pylos.paypal.InvoiceClient;
import com.dreadvoice.pylos.paypal.dto.BillingInfo;
import com.dreadvoice.pylos.paypal.dto.InvoiceDetail;
import com.dreadvoice.pylos.paypal.dto.InvoiceDraftRequest;
import com.dreadvoice.pylos.paypal.dto.InvoiceItem;
import com.dreadvoice.pylos.paypal.dto.InvoiceName;
import com.dreadvoice.pylos.paypal.dto.InvoiceNotification;
import com.dreadvoice.pylos.paypal.dto.InvoiceRecipient;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ActionExecutor {

    private static final String INVOICE_SUBJECT = "Invoice from Pylos";
    private static final String REMINDER_SUBJECT = "Reminder for your invoice";

    private final InvoiceClient invoiceClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ActionExecutor(InvoiceClient invoiceClient, ObjectMapper objectMapper, Clock clock) {
        this.invoiceClient = invoiceClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String execute(PendingAction action) {
        if (action.getStatus() != ActionStatus.APPROVED) {
            throw new ActionStateException("Only approved actions can be executed, but "
                    + action.getId() + " is " + action.getStatus());
        }
        return switch (action.getActionType()) {
            case SEND_INVOICE -> sendInvoice(action);
            case SEND_REMINDER -> sendReminder(action);
            case REFUND, ADD_TRACKING, DISPUTE_RESPONSE ->
                    throw new ActionStateException("Action type " + action.getActionType()
                            + " cannot be executed yet");
        };
    }

    private String sendInvoice(PendingAction action) {
        SendInvoicePayload payload = read(action.getPayload(), SendInvoicePayload.class);
        String currencyCode = payload.currencyCode().toUpperCase();

        List<InvoiceItem> items = payload.items().stream()
                .map(item -> new InvoiceItem(
                        item.name(),
                        item.description(),
                        item.quantity(),
                        new Money(currencyCode, item.unitAmount())))
                .toList();

        InvoiceDraftRequest request = new InvoiceDraftRequest(
                new InvoiceDetail(
                        invoiceClient.generateNextInvoiceNumber(),
                        currencyCode,
                        LocalDate.now(clock.withZone(ZoneOffset.UTC)),
                        payload.note()),
                null,
                List.of(new InvoiceRecipient(new BillingInfo(
                        new InvoiceName(payload.recipientGivenName(), payload.recipientSurname()),
                        payload.recipientEmail()))),
                items);

        InvoiceSummary sent = invoiceClient.createAndSend(
                request,
                new InvoiceNotification(INVOICE_SUBJECT, payload.note(), false, true),
                action.getIdempotencyKey());

        return sent.id();
    }

    private String sendReminder(PendingAction action) {
        SendReminderPayload payload = read(action.getPayload(), SendReminderPayload.class);

        invoiceClient.remind(payload.invoiceId(), new InvoiceNotification(
                payload.subject() == null ? REMINDER_SUBJECT : payload.subject(),
                payload.note(),
                false,
                true));

        return payload.invoiceId();
    }

    private <T> T read(String payloadJson, Class<T> type) {
        try {
            return objectMapper.readValue(payloadJson, type);
        } catch (JsonProcessingException exception) {
            throw new ActionExecutionException(
                    "Action payload could not be read as " + type.getSimpleName(), exception);
        }
    }
}
