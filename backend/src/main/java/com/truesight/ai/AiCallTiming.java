package com.truesight.ai;

import java.time.Instant;

/** One call's start, outcome and duration, as {@link AiCallRepository#findTimingsBetween} reads them. */
public record AiCallTiming(Instant startedAt, AiOutcome outcome, int durationMs) {
}
