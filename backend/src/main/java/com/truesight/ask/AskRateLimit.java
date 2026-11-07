package com.truesight.ask;

import com.truesight.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one user from sending Gemini a question every moment: at most {@link #MOST_QUESTIONS} a minute. Every question
 * costs a call to Gemini with the whole portfolio in it. Kept in memory, so it starts again when the backend does.
 */
@Component
class AskRateLimit {

    static final int MOST_QUESTIONS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Instant>> asked = new ConcurrentHashMap<>();

    AskRateLimit() {
        this(Clock.systemUTC());
    }

    AskRateLimit(Clock clock) {
        this.clock = clock;
    }

    /** Counts a question for this user, or throws a 429 if they have asked too many in the last minute. */
    void take(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> times = asked.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
                times.removeFirst();
            }
            if (times.size() >= MOST_QUESTIONS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "That is a lot of questions at once. Please wait a minute and ask again.");
            }
            times.addLast(now);
        }
    }
}
