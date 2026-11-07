package com.truesight.briefing;

import com.truesight.news.AlertsResponse;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.relationship.RelationshipResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything a briefing says, already worked out by the same services that serve the screen, so the writer needs no
 * database. {@code graphPng} is the decoded picture of the graph, or null when there is none to show.
 */
public record BriefingData(
        String portfolioName,
        LocalDate date,
        byte[] graphPng,
        List<HoldingResponse> holdings,
        AlertsResponse alerts,
        List<RelationshipResponse> dependencies) {
}
