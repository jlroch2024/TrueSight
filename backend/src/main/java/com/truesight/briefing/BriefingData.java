package com.truesight.briefing;

import com.truesight.news.AlertsResponse;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.relationship.RelationshipResponse;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Everything a briefing says, already worked out by the same services that serve the screen, so the writer needs no
 * database. {@code graphPng} is the decoded picture of the graph, or null when there is none to show.
 */
public record BriefingData(
        String portfolioName,
        LocalDate date,
        ZoneId zone,
        byte[] graphPng,
        List<HoldingResponse> holdings,
        AlertsResponse alerts,
        List<RelationshipResponse> dependencies,
        List<BiggestDependency> biggest) {

    /** A holding's largest named revenue or purchases figure, from its latest period, in the filing's own tags. */
    public record BiggestDependency(String holding, String party, int percent, String measure, int fiscalYear,
                                    String form, LocalDate filed) {
    }
}
