package com.dreadvoice.pylos.agent;

import com.dreadvoice.pylos.action.ActionType;
import com.dreadvoice.pylos.action.ActionValidationResult;
import com.dreadvoice.pylos.action.ActionValidator;
import com.dreadvoice.pylos.action.MoneyAmounts;
import com.dreadvoice.pylos.action.PendingAction;
import com.dreadvoice.pylos.action.PendingActionRepository;
import com.dreadvoice.pylos.action.payload.InvoiceLineItem;
import com.dreadvoice.pylos.action.payload.SendInvoicePayload;
import com.dreadvoice.pylos.action.payload.SendReminderPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class ProposeTools {

    private static final Logger log = LoggerFactory.getLogger(ProposeTools.class);

    private final PendingActionRepository repository;
    private final ActionValidator validator;
    private final ProposedActionTracker tracker;
    private final ObjectMapper objectMapper;

    public ProposeTools(PendingActionRepository repository,
                        ActionValidator validator,
                        ProposedActionTracker tracker,
                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.validator = validator;
        this.tracker = tracker;
        this.objectMapper = objectMapper;
    }

    @Tool("Propose a PayPal invoice for the seller to review. This does not send anything. "
            + "Amounts must be plain decimal strings such as 250.00. Currency must be USD or INR. "
            + "The buyer's first and last name are optional; omit them if the conversation does not "
            + "state them rather than asking the seller for them. "
            + "The seller must approve the proposal before any invoice is sent.")
    public String proposeInvoice(
            @P("The buyer's email address, taken from the conversation")
            String recipientEmail,
            @P(value = "The buyer's first name if the conversation states it", required = false)
            String recipientGivenName,
            @P(value = "The buyer's last name if the conversation states it", required = false)
            String recipientSurname,
            @P("The three letter currency code, either USD or INR")
            String currencyCode,
            @P(value = "A short note for the buyer, if one is useful", required = false)
            String note,
            @P("The line items, each with a name, a quantity and a unit amount as plain decimal strings")
            List<InvoiceLineItem> items) {
        SendInvoicePayload payload = new SendInvoicePayload(
                recipientEmail, recipientGivenName, recipientSurname, currencyCode, note, items);

        return propose(ActionType.SEND_INVOICE, payload, invoiceSummary(payload));
    }

    @Tool("Propose a payment reminder for an invoice the seller already sent. This does not send "
            + "anything. The seller must approve the proposal before any reminder goes out.")
    public String proposeReminder(
            @P("The PayPal invoice id, for example INV2-7YQ5-K2GD-GDGY-962L")
            String invoiceId,
            @P(value = "A subject line for the reminder", required = false)
            String subject,
            @P(value = "A short note for the buyer", required = false)
            String note) {
        SendReminderPayload payload = new SendReminderPayload(invoiceId, subject, note);

        return propose(ActionType.SEND_REMINDER, payload,
                "Reminder for invoice " + invoiceId);
    }

    private String propose(ActionType actionType, Object payload, String summary) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "That proposal could not be prepared. Ask the seller to restate the details.";
        }

        ActionValidationResult validation = validator.validate(actionType, payloadJson);
        if (!validation.valid()) {
            log.debug("Rejected {} proposal with {} violations", actionType, validation.violations().size());
            return "That proposal was rejected before reaching the seller: "
                    + String.join("; ", validation.violations())
                    + ". Correct the details and propose again.";
        }

        PendingAction saved = repository.saveAndFlush(new PendingAction(
                actionType, payloadJson, summary, "action-" + UUID.randomUUID()));
        tracker.record(saved.getId());

        log.info("Proposed {} action {}", actionType, saved.getId());
        return "Proposed for the seller's approval: " + summary
                + ". Nothing has been sent. Approval id " + saved.getId() + ".";
    }

    private String invoiceSummary(SendInvoicePayload payload) {
        BigDecimal total = BigDecimal.ZERO;
        if (payload.items() != null) {
            for (InvoiceLineItem item : payload.items()) {
                BigDecimal quantity = MoneyAmounts.parse(item == null ? null : item.quantity())
                        .orElse(BigDecimal.ZERO);
                BigDecimal unitAmount = MoneyAmounts.parse(item == null ? null : item.unitAmount())
                        .orElse(BigDecimal.ZERO);
                total = total.add(MoneyAmounts.lineTotal(quantity, unitAmount));
            }
        }
        int itemCount = payload.items() == null ? 0 : payload.items().size();
        String currency = payload.currencyCode() == null ? "" : payload.currencyCode().toUpperCase();

        return "Invoice " + currency + " " + total.toPlainString()
                + " to " + payload.recipientEmail()
                + " (" + itemCount + (itemCount == 1 ? " item)" : " items)");
    }
}
