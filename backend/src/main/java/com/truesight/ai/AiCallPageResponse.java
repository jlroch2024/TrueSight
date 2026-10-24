package com.truesight.ai;

import java.util.List;

/** One 50-call page, with enough information for the website's pager. */
public record AiCallPageResponse(
        List<AiCallSummaryResponse> calls,
        int page,
        int totalPages,
        long total) {
}
