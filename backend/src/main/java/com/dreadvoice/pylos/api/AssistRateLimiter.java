package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.config.AssistRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssistRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final AssistRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Counter> clients = new ConcurrentHashMap<>();
    private final Counter global = new Counter();

    public AssistRateLimiter(AssistRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public RateLimitDecision tryAcquire(String client) {
        if (!properties.enabled()) {
            return RateLimitDecision.allow();
        }

        Instant now = clock.instant();
        forget(now);

        Counter counter = clients.computeIfAbsent(key(client), ignored -> new Counter());
        if (!counter.tryAcquire(now, properties.perClientWindow(), properties.perClientLimit())) {
            return RateLimitDecision.deny(
                    "Too many assist requests from this client. Try again shortly.",
                    counter.secondsUntilReset(now, properties.perClientWindow()));
        }

        if (!global.tryAcquire(now, properties.globalWindow(), properties.globalLimit())) {
            return RateLimitDecision.deny(
                    "This demo deployment has used its assist budget for now. Try again later.",
                    global.secondsUntilReset(now, properties.globalWindow()));
        }

        return RateLimitDecision.allow();
    }

    private String key(String client) {
        return client == null || client.isBlank() ? "unknown" : client;
    }

    private void forget(Instant now) {
        if (clients.size() < MAX_TRACKED_CLIENTS) {
            clients.values().removeIf(counter -> counter.idle(now, properties.perClientWindow()));
            return;
        }
        clients.clear();
    }

    private static final class Counter {

        private Instant windowStart;
        private int count;

        synchronized boolean tryAcquire(Instant now, Duration window, int limit) {
            if (windowStart == null || !now.isBefore(windowStart.plus(window))) {
                windowStart = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }

        synchronized long secondsUntilReset(Instant now, Duration window) {
            if (windowStart == null) {
                return 0;
            }
            long seconds = Duration.between(now, windowStart.plus(window)).toSeconds();
            return Math.max(seconds, 1);
        }

        synchronized boolean idle(Instant now, Duration window) {
            return windowStart == null || !now.isBefore(windowStart.plus(window));
        }
    }
}
