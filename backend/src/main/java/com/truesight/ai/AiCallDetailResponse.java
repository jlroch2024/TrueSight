package com.truesight.ai;

import java.time.Instant;

/** Everything an analyst sees after opening one AI call. */
public record AiCallDetailResponse(
        Long id,
        Instant startedAt,
        AiPurpose purpose,
        String model,
        AiOutcome outcome,
        Integer durationMs,
        Integer kept,
        Integer dropped,
        String prompt,
        String response,
        String error,
        Long reportId,
        String reportUrl) {
}
