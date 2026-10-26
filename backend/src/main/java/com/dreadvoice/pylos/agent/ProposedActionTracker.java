package com.dreadvoice.pylos.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProposedActionTracker {

    private final ThreadLocal<List<UUID>> proposed = new ThreadLocal<>();

    public void start() {
        proposed.set(new ArrayList<>());
    }

    public void record(UUID actionId) {
        List<UUID> current = proposed.get();
        if (current != null) {
            current.add(actionId);
        }
    }

    public List<UUID> collect() {
        List<UUID> current = proposed.get();
        proposed.remove();
        return current == null ? List.of() : List.copyOf(current);
    }
}
