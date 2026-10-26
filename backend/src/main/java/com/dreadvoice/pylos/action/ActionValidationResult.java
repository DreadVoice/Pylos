package com.dreadvoice.pylos.action;

import java.util.List;

public record ActionValidationResult(List<String> violations) {

    public ActionValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ActionValidationResult ok() {
        return new ActionValidationResult(List.of());
    }

    public static ActionValidationResult rejected(List<String> violations) {
        return new ActionValidationResult(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
