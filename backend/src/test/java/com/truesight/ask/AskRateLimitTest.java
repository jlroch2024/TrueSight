package com.truesight.ask;

import com.truesight.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** At most 20 questions a minute for each user. */
class AskRateLimitTest {

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-07T09:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void theTwentyFirstQuestionInAMinuteIsRefusedWithA429AndTheNextMinuteIsFree() {
        MovableClock clock = new MovableClock();
        AskRateLimit limit = new AskRateLimit(clock);
        for (int i = 0; i < AskRateLimit.MOST_QUESTIONS; i++) limit.take(1L);

        assertThatThrownBy(() -> limit.take(1L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status().value()).isEqualTo(429);
            assertThat(e.getMessage()).endsWith(".");
        });

        clock.advance(Duration.ofSeconds(61));
        assertThatCode(() -> limit.take(1L)).doesNotThrowAnyException();
    }

    @Test
    void oneUsersQuestionsDoNotCountAgainstAnother() {
        AskRateLimit limit = new AskRateLimit(new MovableClock());
        for (int i = 0; i < AskRateLimit.MOST_QUESTIONS; i++) limit.take(1L);

        assertThatCode(() -> limit.take(2L)).doesNotThrowAnyException();
    }
}
