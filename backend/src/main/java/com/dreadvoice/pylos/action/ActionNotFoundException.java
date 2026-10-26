package com.dreadvoice.pylos.action;

import java.util.UUID;

public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(UUID id) {
        super("No pending action with id " + id);
    }
}
