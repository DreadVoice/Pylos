package com.dreadvoice.pylos.agent;

import java.util.List;
import java.util.UUID;

public record AgentResponse(
        String message,
        List<UUID> proposedActionIds,
        boolean contextTruncated
) {

    public AgentResponse {
        proposedActionIds = proposedActionIds == null ? List.of() : List.copyOf(proposedActionIds);
    }
}
