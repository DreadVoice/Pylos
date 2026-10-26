package com.dreadvoice.pylos.api;

import com.fasterxml.jackson.databind.JsonNode;

public record ApproveActionRequest(
        JsonNode payload
) {
}
