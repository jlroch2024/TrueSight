package com.truesight.news;

import com.truesight.relationship.ReviewStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One alert, including the article, AI judgement and every portfolio holding the event reaches. */
public record AlertResponse(
        Long id,
        AlertStatus status,
        BigDecimal exposure,
        Instant createdAt,
        Article article,
        Assessment assessment,
        List<ReachedHolding> holdings) {

    public record Article(String title, String source, Instant publishedAt, String url) {
    }

    public record Assessment(EventType eventType, Severity severity, Confidence confidence, String summary,
                             String reason) {
    }

    /** Display names and the relationship reached; all are null when the event is about the holding itself. */
    public record ReachedHolding(Long holdingId, String ticker, BigDecimal weight, String supplierName,
                                 String customerName, ReviewStatus reviewStatus, Long relationshipId) {
    }
}
