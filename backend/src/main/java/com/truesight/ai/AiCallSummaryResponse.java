package com.truesight.ai;

import java.time.Instant;

/** One row in the AI Audit Trail or its CSV export. */
public record AiCallSummaryResponse(
        Long id,
        Instant startedAt,
        AiPurpose purpose,
        String model,
        AiOutcome outcome,
        Integer durationMs,
        Integer kept,
        Integer dropped) {
}
