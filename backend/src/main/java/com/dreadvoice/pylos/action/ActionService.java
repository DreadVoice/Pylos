package com.dreadvoice.pylos.action;

import com.dreadvoice.pylos.paypal.PayPalApiException;
import com.dreadvoice.pylos.paypal.PayPalErrorRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final PendingActionRepository repository;
    private final ActionValidator validator;
    private final ActionExecutor executor;
    private final Clock clock;

    public ActionService(PendingActionRepository repository,
                         ActionValidator validator,
                         ActionExecutor executor,
                         Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.executor = executor;
        this.clock = clock;
    }

    public List<PendingAction> list(ActionStatus status) {
        if (status == null) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        return repository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    public PendingAction get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ActionNotFoundException(id));
    }

    public PendingAction approve(UUID id, String editedPayloadJson) {
        PendingAction action = get(id);

        if (action.getStatus() == ActionStatus.EXECUTED) {
            return action;
        }
        if (action.getStatus() != ActionStatus.PROPOSED) {
            throw new ActionStateException("Action " + id + " is " + action.getStatus()
                    + " and can no longer be approved");
        }

        if (StringUtils.hasText(editedPayloadJson)) {
            action.setPayload(editedPayloadJson);
        }

        ActionValidationResult validation =
                validator.validate(action.getActionType(), action.getPayload());
        if (!validation.valid()) {
            throw new ActionValidationException(validation.violations());
        }

        int claimed = repository.compareAndSetStatus(
                id, ActionStatus.PROPOSED, ActionStatus.APPROVED, clock.instant());
        if (claimed == 0) {
            throw new ActionStateException("Action " + id + " was already claimed by another approval");
        }

        PendingAction approved = get(id);
        if (StringUtils.hasText(editedPayloadJson)) {
            approved.setPayload(editedPayloadJson);
            approved = repository.saveAndFlush(approved);
        }

        return executeApproved(approved);
    }

    public PendingAction reject(UUID id) {
        PendingAction action = get(id);

        if (action.getStatus() == ActionStatus.REJECTED) {
            return action;
        }
        if (action.getStatus() != ActionStatus.PROPOSED) {
            throw new ActionStateException("Action " + id + " is " + action.getStatus()
                    + " and can no longer be rejected");
        }

        int rejected = repository.compareAndSetStatus(
                id, ActionStatus.PROPOSED, ActionStatus.REJECTED, clock.instant());
        if (rejected == 0) {
            throw new ActionStateException("Action " + id + " was already claimed by another decision");
        }

        return get(id);
    }

    private PendingAction executeApproved(PendingAction approved) {
        try {
            String resultReference = executor.execute(approved);
            approved.setStatus(ActionStatus.EXECUTED);
            approved.setResultReference(resultReference);
            approved.setErrorMessage(null);
            return repository.saveAndFlush(approved);
        } catch (PayPalApiException exception) {
            log.warn("Action {} failed at PayPal with status {}",
                    approved.getId(), exception.getStatusCode());
            return recordFailure(approved, PayPalErrorRedactor.redact(exception.getResponseBody()));
        } catch (RuntimeException exception) {
            log.warn("Action {} failed during execution: {}",
                    approved.getId(), exception.getClass().getSimpleName());
            return recordFailure(approved, exception.getMessage());
        }
    }

    private PendingAction recordFailure(PendingAction approved, String errorMessage) {
        approved.setStatus(ActionStatus.FAILED);
        approved.setErrorMessage(errorMessage);
        return repository.saveAndFlush(approved);
    }
}
