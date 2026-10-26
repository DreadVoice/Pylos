package com.dreadvoice.pylos.api;

import java.util.List;

public record AssistResponse(
        String message,
        List<ActionView> proposedActions,
        boolean contextTruncated
) {
}
