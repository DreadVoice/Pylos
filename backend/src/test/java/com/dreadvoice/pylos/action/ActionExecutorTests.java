package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.paypal.CaptureLookup;
import com.dreadvoice.pylos.paypal.InvoiceCapture;
import com.dreadvoice.pylos.paypal.InvoiceClient;
import com.dreadvoice.pylos.paypal.RefundClient;
import com.dreadvoice.pylos.paypal.dto.InvoiceDraftRequest;
import com.dreadvoice.pylos.paypal.dto.InvoiceNotification;
import com.dreadvoice.pylos.paypal.dto.InvoiceSummary;
import com.dreadvoice.pylos.paypal.dto.Money;
import com.dreadvoice.pylos.paypal.dto.RefundDetail;
import com.dreadvoice.pylos.paypal.dto.RefundRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActionExecutorTests {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private InvoiceClient invoiceClient;
    private RefundClient refundClient;
    private CaptureLookup captureLookup;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
        invoiceClient = mock(InvoiceClient.class);
        refundClient = mock(RefundClient.class);
        captureLookup = mock(CaptureLookup.class);
        executor = new ActionExecutor(invoiceClient, refundClient, captureLookup, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsInvoiceMappedFromPayloadWithTheIdempotencyKey() {
        when(invoiceClient.generateNextInvoiceNumber()).thenReturn("0042");
        when(invoiceClient.createAndSend(any(), any(), eq("action-42")))
                .thenReturn(new InvoiceSummary("INV2-NEW", "SENT", null, null, null, null, null, null, null));

        PendingAction action = approved(ActionType.SEND_INVOICE, """
                {"recipient_email":"buyer@personal.example.com","recipient_given_name":"Test",
                 "recipient_surname":"Buyer","currency_code":"usd","note":"Thanks",
                 "items":[{"name":"Logo design","description":"Final artwork",
                           "quantity":"2","unit_amount":"125.00"}]}
                """, "action-42");

        String resultReference = executor.execute(action);

        assertThat(resultReference).isEqualTo("INV2-NEW");

        ArgumentCaptor<InvoiceDraftRequest> request = ArgumentCaptor.forClass(InvoiceDraftRequest.class);
        ArgumentCaptor<InvoiceNotification> notification = ArgumentCaptor.forClass(InvoiceNotification.class);
        verify(invoiceClient).createAndSend(request.capture(), notification.capture(), eq("action-42"));

        InvoiceDraftRequest sent = request.getValue();
        assertThat(sent.detail().invoiceNumber()).isEqualTo("0042");
        assertThat(sent.detail().currencyCode()).isEqualTo("USD");
        assertThat(sent.detail().invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(sent.detail().note()).isEqualTo("Thanks");
        assertThat(sent.invoicer()).isNull();
        assertThat(sent.primaryRecipients()).hasSize(1);
        assertThat(sent.primaryRecipients().getFirst().billingInfo().emailAddress())
                .isEqualTo("buyer@personal.example.com");
        assertThat(sent.items()).hasSize(1);
        assertThat(sent.items().getFirst().unitAmount().currencyCode()).isEqualTo("USD");
        assertThat(sent.items().getFirst().unitAmount().value()).isEqualTo("125.00");
        assertThat(sent.items().getFirst().quantity()).isEqualTo("2");
        assertThat(notification.getValue().sendToRecipient()).isTrue();
    }

    @Test
    void sendsReminderForTheReferencedInvoice() {
        PendingAction action = approved(ActionType.SEND_REMINDER, """
                {"invoice_id":"INV2-7YQ5-K2GD-GDGY-962L","subject":"Still unpaid","note":"Gentle nudge"}
                """, "action-7");

        String resultReference = executor.execute(action);

        assertThat(resultReference).isEqualTo("INV2-7YQ5-K2GD-GDGY-962L");

        ArgumentCaptor<InvoiceNotification> notification = ArgumentCaptor.forClass(InvoiceNotification.class);
        verify(invoiceClient).remind(
                eq("INV2-7YQ5-K2GD-GDGY-962L"), notification.capture(), eq("action-7"));
        assertThat(notification.getValue().subject()).isEqualTo("Still unpaid");
        assertThat(notification.getValue().note()).isEqualTo("Gentle nudge");
        verify(invoiceClient, never()).createAndSend(any(), any(), any());
    }

    @Test
    void refusesToExecuteAnythingThatIsNotApproved() {
        for (ActionStatus status : new ActionStatus[]{
                ActionStatus.PROPOSED, ActionStatus.EXECUTED, ActionStatus.REJECTED, ActionStatus.FAILED}) {
            PendingAction action = new PendingAction(
                    ActionType.SEND_INVOICE, "{}", "Invoice", "key-" + status);
            action.setStatus(status);

            assertThatThrownBy(() -> executor.execute(action))
                    .isInstanceOf(ActionStateException.class)
                    .hasMessageContaining("Only approved actions can be executed");
        }

        verifyNoInteractions(invoiceClient);
    }

    @Test
    void refusesActionTypesThatAreNotImplementedYet() {
        PendingAction action = approved(ActionType.ADD_TRACKING, "{}", "action-tracking");

        assertThatThrownBy(() -> executor.execute(action))
                .isInstanceOf(ActionStateException.class)
                .hasMessageContaining("cannot be executed yet");

        verifyNoInteractions(invoiceClient);
        verifyNoInteractions(refundClient);
    }

    @Test
    void refundsTheCaptureBehindTheInvoiceWithTheIdempotencyKey() {
        when(captureLookup.forInvoice("INV2-GS4Q-CBHK-S6QM-CC5V")).thenReturn(Optional.of(
                new InvoiceCapture("INV2-GS4Q-CBHK-S6QM-CC5V", "2S7263346H6677809", "COMPLETED",
                        new Money("USD", "60.00"), new Money("USD", "0.00"))));
        when(refundClient.refund(eq("2S7263346H6677809"), any(), eq("action-refund")))
                .thenReturn(new RefundDetail("1JU08902781691411", "COMPLETED",
                        new Money("USD", "15.00"), null));

        PendingAction action = approved(ActionType.REFUND, """
                {"invoice_id":"INV2-GS4Q-CBHK-S6QM-CC5V","currency_code":"usd","amount":"15.00",
                 "note_to_payer":"Sorry about the damaged item"}
                """, "action-refund");

        assertThat(executor.execute(action)).isEqualTo("1JU08902781691411");

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundClient).refund(eq("2S7263346H6677809"), captor.capture(), eq("action-refund"));
        assertThat(captor.getValue().amount()).isEqualTo(new Money("USD", "15.00"));
        assertThat(captor.getValue().invoiceId()).isEqualTo("INV2-GS4Q-CBHK-S6QM-CC5V");
        assertThat(captor.getValue().noteToPayer()).isEqualTo("Sorry about the damaged item");
        verifyNoInteractions(invoiceClient);
    }

    @Test
    void refusesToRefundAnInvoiceWithNoCapture() {
        when(captureLookup.forInvoice("INV2-GS4Q-CBHK-S6QM-CC5V")).thenReturn(Optional.empty());

        PendingAction action = approved(ActionType.REFUND,
                "{\"invoice_id\":\"INV2-GS4Q-CBHK-S6QM-CC5V\",\"currency_code\":\"USD\","
                        + "\"amount\":\"15.00\"}", "action-refund");

        assertThatThrownBy(() -> executor.execute(action))
                .isInstanceOf(ActionExecutionException.class)
                .hasMessageContaining("has no PayPal payment to refund");

        verifyNoInteractions(refundClient);
    }

    @Test
    void wrapsUnreadablePayloadsWithoutCallingPayPal() {
        PendingAction action = approved(ActionType.SEND_INVOICE, "{not json", "action-bad");

        assertThatThrownBy(() -> executor.execute(action))
                .isInstanceOf(ActionExecutionException.class)
                .hasMessageContaining("SendInvoicePayload");

        verify(invoiceClient, never()).createAndSend(any(), any(), any());
    }

    private static PendingAction approved(ActionType actionType, String payload, String idempotencyKey) {
        PendingAction action = new PendingAction(actionType, payload, "Summary", idempotencyKey);
        action.setStatus(ActionStatus.APPROVED);
        return action;
    }
}
