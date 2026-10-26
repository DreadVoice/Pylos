package com.dreadvoice.pylos.action;

import java.util.List;

public class ActionValidationException extends RuntimeException {

    private final List<String> violations;

    public ActionValidationException(List<String> violations) {
        super("Action payload failed validation");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
