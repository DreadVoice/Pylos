package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.config.AssistRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AssistRateLimiterTests {

    private static final Instant START = Instant.parse("2026-11-05T09:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);

    private final Clock movingClock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private AssistRateLimiter limiterWith(int perClient, int global) {
        return new AssistRateLimiter(
                new AssistRateLimitProperties(true, perClient, Duration.ofMinutes(1),
                        global, Duration.ofDays(1)),
                movingClock);
    }

    private void advance(Duration by) {
        now.updateAndGet(instant -> instant.plus(by));
    }

    @Test
    void letsAClientThroughUpToItsLimit() {
        AssistRateLimiter limiter = limiterWith(3, 100);

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(limiter.tryAcquire("10.0.0.1").allowed())
                    .as("attempt %d", attempt)
                    .isTrue();
        }

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
    }

    @Test
    void tellsTheCallerHowLongToWait() {
        AssistRateLimiter limiter = limiterWith(1, 100);
        limiter.tryAcquire("10.0.0.1");

        advance(Duration.ofSeconds(20));
        RateLimitDecision decision = limiter.tryAcquire("10.0.0.1");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(40);
        assertThat(decision.reason()).contains("Too many assist requests");
    }

    @Test
    void forgivesTheClientOnceItsWindowHasPassed() {
        AssistRateLimiter limiter = limiterWith(2, 100);
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();

        advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void countsEachClientSeparately() {
        AssistRateLimiter limiter = limiterWith(1, 100);

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isFalse();
    }

    @Test
    void stopsEveryoneOnceTheDailyBudgetIsGone() {
        AssistRateLimiter limiter = limiterWith(100, 3);

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.2").allowed()).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.3").allowed()).isTrue();

        RateLimitDecision decision = limiter.tryAcquire("10.0.0.4");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("assist budget");
    }

    @Test
    void theDailyBudgetSurvivesAClientWindowRollingOver() {
        AssistRateLimiter limiter = limiterWith(100, 2);
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        advance(Duration.ofMinutes(5));

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();
    }

    @Test
    void theDailyBudgetComesBackTheNextDay() {
        AssistRateLimiter limiter = limiterWith(100, 1);
        limiter.tryAcquire("10.0.0.1");
        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isFalse();

        advance(Duration.ofDays(1).plusSeconds(1));

        assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
    }

    @Test
    void doesNotSpendTheDailyBudgetOnARequestItAlreadyRefused() {
        AssistRateLimiter limiter = limiterWith(1, 5);
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        for (int client = 2; client <= 5; client++) {
            assertThat(limiter.tryAcquire("10.0.0." + client).allowed())
                    .as("client %d", client)
                    .isTrue();
        }
    }

    @Test
    void treatsAMissingClientAsOneCaller() {
        AssistRateLimiter limiter = limiterWith(1, 100);

        assertThat(limiter.tryAcquire(null).allowed()).isTrue();
        assertThat(limiter.tryAcquire("  ").allowed()).isFalse();
    }

    @Test
    void lettingEverythingThroughIsAConfigurationChoice() {
        AssistRateLimiter limiter = new AssistRateLimiter(
                new AssistRateLimitProperties(false, 1, Duration.ofMinutes(1), 1, Duration.ofDays(1)),
                movingClock);

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(limiter.tryAcquire("10.0.0.1").allowed()).isTrue();
        }
    }

    @Test
    void defaultsToSensibleWindowsWhenNoneAreConfigured() {
        AssistRateLimitProperties properties =
                new AssistRateLimitProperties(true, 5, null, 200, null);

        assertThat(properties.perClientWindow()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.globalWindow()).isEqualTo(Duration.ofDays(1));
    }
}
