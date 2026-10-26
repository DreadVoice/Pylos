package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.action.payload.InvoiceLineItem;
import com.dreadvoice.pylos.action.payload.SendInvoicePayload;
import com.dreadvoice.pylos.action.payload.SendReminderPayload;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ActionValidator {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "INR");
    private static final int MAX_LINE_ITEMS = 100;
    private static final String UNREADABLE_PAYLOAD = "payload: could not be read as the expected shape";

    private final ObjectReader strictReader;
    private final Validator validator;

    public ActionValidator(ObjectMapper objectMapper, Validator validator) {
        this.strictReader = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public ActionValidationResult validate(ActionType actionType, String payloadJson) {
        if (actionType == null) {
            return ActionValidationResult.rejected(List.of("actionType: must not be null"));
        }
        return switch (actionType) {
            case SEND_INVOICE -> validateSendInvoice(payloadJson);
            case SEND_REMINDER -> validateSendReminder(payloadJson);
            case REFUND, ADD_TRACKING, DISPUTE_RESPONSE ->
                    ActionValidationResult.rejected(
                            List.of("actionType: " + actionType + " is not supported yet"));
        };
    }

    private ActionValidationResult validateSendInvoice(String payloadJson) {
        SendInvoicePayload payload;
        try {
            payload = strictReader.readValue(payloadJson, SendInvoicePayload.class);
        } catch (IOException unreadable) {
            return ActionValidationResult.rejected(List.of(UNREADABLE_PAYLOAD));
        }
        if (payload == null) {
            return ActionValidationResult.rejected(List.of("payload: must not be empty"));
        }

        List<String> violations = new ArrayList<>(constraintViolations(payload));
        violations.addAll(invoiceAmountViolations(payload));

        return result(violations);
    }

    private ActionValidationResult validateSendReminder(String payloadJson) {
        SendReminderPayload payload;
        try {
            payload = strictReader.readValue(payloadJson, SendReminderPayload.class);
        } catch (IOException unreadable) {
            return ActionValidationResult.rejected(List.of(UNREADABLE_PAYLOAD));
        }
        if (payload == null) {
            return ActionValidationResult.rejected(List.of("payload: must not be empty"));
        }

        return result(new ArrayList<>(constraintViolations(payload)));
    }

    private List<String> invoiceAmountViolations(SendInvoicePayload payload) {
        List<String> violations = new ArrayList<>();

        if (payload.currencyCode() != null
                && !SUPPORTED_CURRENCIES.contains(payload.currencyCode().toUpperCase())) {
            violations.add("currencyCode: " + payload.currencyCode() + " is not a supported currency");
        }

        List<InvoiceLineItem> items = payload.items();
        if (items == null || items.isEmpty()) {
            return violations;
        }
        if (items.size() > MAX_LINE_ITEMS) {
            violations.add("items: must not contain more than " + MAX_LINE_ITEMS + " line items");
            return violations;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            InvoiceLineItem item = items.get(index);
            if (item == null) {
                violations.add("items[" + index + "]: must not be null");
                continue;
            }

            Optional<BigDecimal> quantity = MoneyAmounts.parse(item.quantity());
            Optional<BigDecimal> unitAmount = MoneyAmounts.parse(item.unitAmount());

            if (quantity.isEmpty()) {
                violations.add("items[" + index + "].quantity: " + describe(item.quantity())
                        + " is not a plain decimal with at most 2 decimal places");
            } else if (quantity.get().signum() <= 0) {
                violations.add("items[" + index + "].quantity: must be greater than zero");
            }

            if (unitAmount.isEmpty()) {
                violations.add("items[" + index + "].unitAmount: " + describe(item.unitAmount())
                        + " is not a plain decimal with at most 2 decimal places");
            } else if (unitAmount.get().signum() < 0) {
                violations.add("items[" + index + "].unitAmount: must not be negative");
            }

            if (quantity.isPresent() && unitAmount.isPresent()
                    && quantity.get().signum() > 0 && unitAmount.get().signum() >= 0) {
                total = total.add(MoneyAmounts.lineTotal(quantity.get(), unitAmount.get()));
            }
        }

        if (violations.isEmpty() && total.signum() <= 0) {
            violations.add("items: total amount must be greater than zero");
        }

        return violations;
    }

    private <T> List<String> constraintViolations(T payload) {
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .toList();
    }

    private ActionValidationResult result(List<String> violations) {
        if (violations.isEmpty()) {
            return ActionValidationResult.ok();
        }
        return ActionValidationResult.rejected(violations.stream()
                .sorted(Comparator.naturalOrder())
                .distinct()
                .toList());
    }

    private String describe(String value) {
        return value == null ? "null" : "'" + value + "'";
    }
}
