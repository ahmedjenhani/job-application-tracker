package com.jobtracker.ai;

import com.jobtracker.common.ApiExceptions.RateLimitException;
import com.jobtracker.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window limiter. It protects the shared free-tier quota from a single user hammering
 * the AI endpoints. State is in memory, which is fine for a single-instance deployment.
 */
@Component
public class AiRateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final int perMinute;
    private final int perDay;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public AiRateLimiter(AppProperties props, Clock clock) {
        this.perMinute = props.ai().rateLimit().perMinute();
        this.perDay = props.ai().rateLimit().perDay();
        this.clock = clock;
    }

    /** Records one call for the user, or throws {@link RateLimitException} when a limit is reached. */
    public void acquire(Long userId) {
        Deque<Instant> window = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            Instant now = clock.instant();
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(DAY))) {
                window.pollFirst();
            }
            if (window.size() >= perDay) {
                long wait = Duration.between(now, window.peekFirst().plus(DAY)).toSeconds() + 1;
                throw new RateLimitException("Daily AI limit reached (" + perDay + " requests)", wait);
            }
            Instant minuteAgo = now.minus(MINUTE);
            long lastMinute = window.stream().filter(t -> t.isAfter(minuteAgo)).count();
            if (lastMinute >= perMinute) {
                Instant oldestInMinute = window.stream().filter(t -> t.isAfter(minuteAgo)).findFirst().orElse(now);
                long wait = Duration.between(now, oldestInMinute.plus(MINUTE)).toSeconds() + 1;
                throw new RateLimitException("Too many AI requests, please wait a moment", wait);
            }
            window.addLast(now);
        }
    }
}
