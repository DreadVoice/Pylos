package com.dreadvoice.pylos.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StrandedApprovalRecovery {

    private static final Logger log = LoggerFactory.getLogger(StrandedApprovalRecovery.class);

    private final ActionService actionService;

    public StrandedApprovalRecovery(ActionService actionService) {
        this.actionService = actionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup() {
        try {
            List<PendingAction> resumed = actionService.recoverStrandedApprovals();
            if (!resumed.isEmpty()) {
                log.info("Resumed {} approved action(s) left over from a previous run", resumed.size());
            }
        } catch (RuntimeException exception) {
            log.error("Could not resume approved actions on startup: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
