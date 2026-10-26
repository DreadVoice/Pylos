package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.paypal.PayPalApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@TestPropertySource(properties = {
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class ActionServiceTests {

    private static final String VALID_INVOICE = """
            {"recipient_email":"buyer@personal.example.com","currency_code":"USD",
             "items":[{"name":"Logo design","quantity":"1","unit_amount":"250.00"}]}
            """;

    @Autowired
    private PendingActionRepository repository;

    private ActionExecutor executor;
    private ActionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        ActionValidator validator = new ActionValidator(objectMapper, factory.getValidator());
        executor = mock(ActionExecutor.class);
        service = new ActionService(repository, validator, executor,
                Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void approveValidatesExecutesAndRecordsTheResult() {
        when(executor.execute(any())).thenReturn("INV2-NEW");
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-1", VALID_INVOICE));

        PendingAction executed = service.approve(proposed.getId(), null);

        assertThat(executed.getStatus()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(executed.getResultReference()).isEqualTo("INV2-NEW");
        assertThat(executed.getErrorMessage()).isNull();
    }

    @Test
    void approveAppliesAndRevalidatesAnEditedPayload() {
        when(executor.execute(any())).thenReturn("INV2-EDITED");
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-2", VALID_INVOICE));

        String edited = """
                {"recipient_email":"other@personal.example.com","currency_code":"USD",
                 "items":[{"name":"Logo design","quantity":"1","unit_amount":"300.00"}]}
                """;

        PendingAction executed = service.approve(proposed.getId(), edited);

        assertThat(executed.getStatus()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(executed.getPayload()).contains("other@personal.example.com");
        assertThat(executed.getPayload()).contains("300.00");
    }

    @Test
    void approveRejectsAnEditedPayloadThatFailsValidation() {
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-3", VALID_INVOICE));

        String edited = """
                {"recipient_email":"buyer@personal.example.com","currency_code":"USD",
                 "items":[{"name":"Logo design","quantity":"1","unit_amount":"-250.00"}]}
                """;

        assertThatThrownBy(() -> service.approve(proposed.getId(), edited))
                .isInstanceOf(ActionValidationException.class);

        assertThat(repository.findById(proposed.getId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.PROPOSED);
        verify(executor, never()).execute(any());
    }

    @Test
    void approveRefusesAProposalThatWasNeverValidPayload() {
        PendingAction proposed = repository.saveAndFlush(
                invoiceAction("approve-4", "{\"recipient_email\":\"nope\"}"));

        assertThatThrownBy(() -> service.approve(proposed.getId(), null))
                .isInstanceOf(ActionValidationException.class);

        verify(executor, never()).execute(any());
    }

    @Test
    void approveIsIdempotentOnceExecuted() {
        when(executor.execute(any())).thenReturn("INV2-ONCE");
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-5", VALID_INVOICE));

        PendingAction first = service.approve(proposed.getId(), null);
        PendingAction second = service.approve(proposed.getId(), null);

        assertThat(first.getResultReference()).isEqualTo("INV2-ONCE");
        assertThat(second.getResultReference()).isEqualTo("INV2-ONCE");
        assertThat(second.getStatus()).isEqualTo(ActionStatus.EXECUTED);
        verify(executor).execute(any());
    }

    @Test
    void approveConflictsOnARejectedAction() {
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-6", VALID_INVOICE));
        service.reject(proposed.getId());

        assertThatThrownBy(() -> service.approve(proposed.getId(), null))
                .isInstanceOf(ActionStateException.class)
                .hasMessageContaining("can no longer be approved");
    }

    @Test
    void executionFailureRecordsFailedWithRedactedError() {
        when(executor.execute(any())).thenThrow(new PayPalApiException(
                "PayPal invoicing request failed with status 422",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "{\"name\":\"VALIDATION_ERROR\",\"email\":\"buyer@personal.example.com\"}",
                null));
        PendingAction proposed = repository.saveAndFlush(invoiceAction("approve-7", VALID_INVOICE));

        PendingAction failed = service.approve(proposed.getId(), null);

        assertThat(failed.getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("VALIDATION_ERROR");
        assertThat(failed.getErrorMessage()).contains("[redacted-email]");
        assertThat(failed.getErrorMessage()).doesNotContain("buyer@personal.example.com");
        assertThat(failed.getResultReference()).isNull();
    }

    @Test
    void rejectMovesAProposalToRejectedAndIsIdempotent() {
        PendingAction proposed = repository.saveAndFlush(invoiceAction("reject-1", VALID_INVOICE));

        assertThat(service.reject(proposed.getId()).getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(service.reject(proposed.getId()).getStatus()).isEqualTo(ActionStatus.REJECTED);
        verify(executor, never()).execute(any());
    }

    @Test
    void rejectConflictsOnAnExecutedAction() {
        when(executor.execute(any())).thenReturn("INV2-DONE");
        PendingAction proposed = repository.saveAndFlush(invoiceAction("reject-2", VALID_INVOICE));
        service.approve(proposed.getId(), null);

        assertThatThrownBy(() -> service.reject(proposed.getId()))
                .isInstanceOf(ActionStateException.class)
                .hasMessageContaining("can no longer be rejected");
    }

    @Test
    void getThrowsForAnUnknownId() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(ActionNotFoundException.class);
    }

    @Test
    void listFiltersByStatusAndReturnsEverythingWhenUnfiltered() {
        repository.saveAndFlush(invoiceAction("list-1", VALID_INVOICE));
        PendingAction rejected = repository.saveAndFlush(invoiceAction("list-2", VALID_INVOICE));
        service.reject(rejected.getId());

        assertThat(service.list(ActionStatus.PROPOSED)).hasSize(1);
        assertThat(service.list(ActionStatus.REJECTED)).hasSize(1);
        assertThat(service.list(null)).hasSize(2);
    }

    private static PendingAction invoiceAction(String idempotencyKey, String payload) {
        return new PendingAction(ActionType.SEND_INVOICE, payload,
                "Invoice USD 250.00 to Test Buyer", idempotencyKey);
    }
}
