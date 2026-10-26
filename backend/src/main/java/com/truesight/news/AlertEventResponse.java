package com.truesight.news;

import com.truesight.relationship.RelationshipConfidence;
import com.truesight.relationship.ReviewStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One event reported by one or more articles and the portfolio holdings it reaches. */
public record AlertEventResponse(
        List<Long> alertIds,
        AlertStatus status,
        BigDecimal exposure,
        Headline headline,
        List<Source> sources,
        List<ReachedHolding> holdings) {

    public record Headline(Article article, Assessment assessment) {
    }

    public record Article(String title, String source, Instant publishedAt, String url) {
    }

    public record Assessment(EventType eventType, Severity severity, Confidence confidence, String summary,
                             String reason) {
    }

    public record Source(String title, String source, Instant publishedAt, String url) {
    }

    /**
     * Display names and the path reached, and how confident TrueSight is about it (for a two-step path, the weaker of
     * its two relationships). Names, relationship and confidence are null for an event at the holding itself.
     */
    public record ReachedHolding(Long holdingId, String ticker, BigDecimal weight, boolean indirect,
                                 String supplierName, String middleName, String customerName,
                                 ReviewStatus reviewStatus, Long relationshipId, RelationshipConfidence confidence) {
    }
}
