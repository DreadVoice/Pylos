package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionStatus;
import com.dreadvoice.pylos.action.ActionType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ActionView(
        UUID id,
        ActionType actionType,
        ActionStatus status,
        String summary,
        JsonNode payload,
        String resultReference,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
