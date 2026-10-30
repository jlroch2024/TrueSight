package com.truesight.news;

import com.truesight.relationship.RelationshipResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * The answer to "what if this company stops supplying?": the holdings it reaches downstream, how much of the
 * portfolio that is, and the links on the way. Worked out when asked, never saved.
 *
 * <p>{@code exposure} is the total weight of the holdings reached (12.5 means 12.5%) when {@code weighted}, and the
 * number of holdings reached otherwise, out of {@code holdingCount}. {@code links} is every relationship on a path,
 * with its review status and confidence; {@code criticalLinks} is those of them the report calls a sole or single
 * source, with the sentence ({@code criticalityEvidence}) and the report's address. {@code assumptions} is the line
 * to show with every result.
 */
public record WhatIfResponse(
        RelationshipResponse.CompanySummary company,
        List<ReachedHolding> holdings,
        BigDecimal exposure,
        boolean weighted,
        int holdingCount,
        List<RelationshipResponse> criticalLinks,
        List<RelationshipResponse> links,
        String assumptions) {

    /**
     * A holding reached. {@code path} is the ids of the relationships from the stopped company to it, in order: one
     * for a holding it supplies, two for a holding reached in two steps ({@code indirect}), none when the stopped
     * company is the holding itself.
     */
    public record ReachedHolding(Long holdingId, String ticker, BigDecimal weight, boolean indirect, List<Long> path) {
    }
}
