package com.dreadvoice.pylos.action;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class PendingActionRepositoryTests {

    @Autowired
    private PendingActionRepository repository;

    @Test
    void savesAndReadsBackAProposedAction() {
        PendingAction saved = repository.saveAndFlush(invoiceAction("action-1"));

        Optional<PendingAction> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        PendingAction action = found.orElseThrow();
        assertThat(action.getId()).isNotNull();
        assertThat(action.getActionType()).isEqualTo(ActionType.SEND_INVOICE);
        assertThat(action.getStatus()).isEqualTo(ActionStatus.PROPOSED);
        assertThat(action.getPayload()).contains("buyer@personal.example.com");
        assertThat(action.getSummary()).isEqualTo("Invoice USD 250.00 to Test Buyer");
        assertThat(action.getIdempotencyKey()).isEqualTo("action-1");
        assertThat(action.getResultReference()).isNull();
        assertThat(action.getErrorMessage()).isNull();
    }

    @Test
    void populatesTimestampsOnInsert() {
        PendingAction saved = repository.saveAndFlush(invoiceAction("action-2"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
    }

    @Test
    void movesUpdatedAtForwardOnChange() {
        PendingAction saved = repository.saveAndFlush(invoiceAction("action-3"));
        java.time.Instant createdAt = saved.getCreatedAt();

        saved.setStatus(ActionStatus.EXECUTED);
        saved.setResultReference("INV2-ABCD");
        PendingAction updated = repository.saveAndFlush(saved);

        assertThat(updated.getStatus()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(updated.getResultReference()).isEqualTo("INV2-ABCD");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void rejectsDuplicateIdempotencyKeys() {
        repository.saveAndFlush(invoiceAction("duplicate-key"));

        assertThatThrownBy(() -> repository.saveAndFlush(invoiceAction("duplicate-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByIdempotencyKey() {
        repository.saveAndFlush(invoiceAction("lookup-key"));

        assertThat(repository.findByIdempotencyKey("lookup-key")).isPresent();
        assertThat(repository.findByIdempotencyKey("missing-key")).isEmpty();
    }

    @Test
    void filtersByStatusNewestFirst() {
        repository.saveAndFlush(invoiceAction("proposed-1"));
        PendingAction executed = repository.saveAndFlush(invoiceAction("executed-1"));
        executed.setStatus(ActionStatus.EXECUTED);
        repository.saveAndFlush(executed);

        List<PendingAction> proposed = repository.findAllByStatusOrderByCreatedAtDesc(ActionStatus.PROPOSED);

        assertThat(proposed).hasSize(1);
        assertThat(proposed.getFirst().getIdempotencyKey()).isEqualTo("proposed-1");
        assertThat(repository.findAllByOrderByCreatedAtDesc()).hasSize(2);
    }

    @Test
    void storesReminderActionsToo() {
        PendingAction reminder = new PendingAction(
                ActionType.SEND_REMINDER,
                "{\"invoiceId\":\"INV2-ABCD\"}",
                "Remind Test Buyer about INV2-ABCD",
                "reminder-1");

        PendingAction saved = repository.saveAndFlush(reminder);

        assertThat(saved.getActionType()).isEqualTo(ActionType.SEND_REMINDER);
        assertThat(saved.getStatus()).isEqualTo(ActionStatus.PROPOSED);
    }

    private static PendingAction invoiceAction(String idempotencyKey) {
        return new PendingAction(
                ActionType.SEND_INVOICE,
                "{\"recipientEmail\":\"buyer@personal.example.com\",\"currencyCode\":\"USD\"}",
                "Invoice USD 250.00 to Test Buyer",
                idempotencyKey);
    }
}
