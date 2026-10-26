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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
class StrandedApprovalRecoveryTests {

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
    void finishesAnActionThatWasLeftApproved() {
        when(executor.execute(any())).thenReturn("INV2-RESUMED");
        PendingAction stranded = repository.saveAndFlush(approvedAction("stranded-1"));

        List<PendingAction> resumed = service.recoverStrandedApprovals();

        assertThat(resumed).hasSize(1);
        assertThat(resumed.getFirst().getStatus()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(resumed.getFirst().getResultReference()).isEqualTo("INV2-RESUMED");
        assertThat(repository.findById(stranded.getId()).orElseThrow().getStatus())
                .isEqualTo(ActionStatus.EXECUTED);
    }

    @Test
    void marksAResumedActionFailedWhenPayPalRejectsIt() {
        when(executor.execute(any())).thenThrow(new PayPalApiException(
                "PayPal invoicing request failed with status 422",
                HttpStatus.UNPROCESSABLE_ENTITY,
                "{\"name\":\"VALIDATION_ERROR\",\"email\":\"buyer@personal.example.com\"}",
                null));
        repository.saveAndFlush(approvedAction("stranded-2"));

        List<PendingAction> resumed = service.recoverStrandedApprovals();

        assertThat(resumed).hasSize(1);
        assertThat(resumed.getFirst().getStatus()).isEqualTo(ActionStatus.FAILED);
        assertThat(resumed.getFirst().getErrorMessage()).contains("[redacted-email]");
        assertThat(resumed.getFirst().getErrorMessage()).doesNotContain("buyer@personal.example.com");
    }

    @Test
    void resumesEveryStrandedAction() {
        when(executor.execute(any())).thenReturn("INV2-RESUMED");
        repository.saveAndFlush(approvedAction("stranded-3"));
        repository.saveAndFlush(approvedAction("stranded-4"));

        assertThat(service.recoverStrandedApprovals()).hasSize(2);
    }

    @Test
    void leavesProposedRejectedExecutedAndFailedActionsAlone() {
        repository.saveAndFlush(new PendingAction(
                ActionType.SEND_INVOICE, VALID_INVOICE, "Proposed", "untouched-proposed"));
        repository.saveAndFlush(actionWithStatus("untouched-rejected", ActionStatus.REJECTED));
        repository.saveAndFlush(actionWithStatus("untouched-executed", ActionStatus.EXECUTED));
        repository.saveAndFlush(actionWithStatus("untouched-failed", ActionStatus.FAILED));

        assertThat(service.recoverStrandedApprovals()).isEmpty();
        verify(executor, never()).execute(any());
    }

    @Test
    void doesNothingWhenThereIsNothingStranded() {
        assertThat(service.recoverStrandedApprovals()).isEmpty();
        verify(executor, never()).execute(any());
    }

    private static PendingAction approvedAction(String idempotencyKey) {
        return actionWithStatus(idempotencyKey, ActionStatus.APPROVED);
    }

    private static PendingAction actionWithStatus(String idempotencyKey, ActionStatus status) {
        PendingAction action = new PendingAction(
                ActionType.SEND_INVOICE, VALID_INVOICE,
                "Invoice USD 250.00 to Test Buyer", idempotencyKey);
        action.setStatus(status);
        return action;
    }
}
