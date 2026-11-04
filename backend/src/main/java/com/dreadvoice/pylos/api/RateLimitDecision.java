package com.dreadvoice.pylos.api;

public record RateLimitDecision(
        boolean allowed,
        String reason,
        long retryAfterSeconds
) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, null, 0);
    }

    public static RateLimitDecision deny(String reason, long retryAfterSeconds) {
        return new RateLimitDecision(false, reason, retryAfterSeconds);
    }
}
