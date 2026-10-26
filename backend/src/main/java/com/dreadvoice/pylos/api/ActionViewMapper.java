package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.PendingAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;

@Component
public class ActionViewMapper {

    private final ObjectMapper objectMapper;

    public ActionViewMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ActionView toView(PendingAction action) {
        return new ActionView(
                action.getId(),
                action.getActionType(),
                action.getStatus(),
                action.getSummary(),
                readPayload(action.getPayload()),
                action.getResultReference(),
                action.getErrorMessage(),
                action.getCreatedAt(),
                action.getUpdatedAt());
    }

    private JsonNode readPayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            return NullNode.getInstance();
        }
    }
}
