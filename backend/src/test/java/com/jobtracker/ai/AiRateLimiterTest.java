package com.jobtracker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtracker.common.ApiExceptions.RateLimitException;
import com.jobtracker.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiRateLimiterTest {

    private MutableClock clock;
    private AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-14T10:00:00Z"));
        AppProperties props = new AppProperties(null, null, null, null, null,
                new AppProperties.Ai(null, new AppProperties.RateLimit(3, 5)));
        limiter = new AiRateLimiter(props, clock);
    }

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1L);
        }
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(RateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 61L));
    }

    @Test
    void windowSlidesAfterAMinute() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1L);
        }
        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void usersAreLimitedIndependently() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1L);
        }
        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void dailyLimitApplies() {
        for (int i = 0; i < 5; i++) {
            limiter.acquire(1L);
            clock.advance(Duration.ofSeconds(61)); // stay under the per-minute limit
        }
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Daily");

        clock.advance(Duration.ofHours(24));
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    /** Minimal controllable clock for deterministic time-based tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

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
            return now;
        }
    }
}
