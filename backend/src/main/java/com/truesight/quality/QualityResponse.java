package com.truesight.quality;

import java.time.LocalDate;
import java.util.List;

/**
 * The AI's track record: one entry per week for each AI step, oldest week first. Every figure is a count; the website
 * works out the shares. A week whose total is 0 had no decisions.
 */
public record QualityResponse(
        List<RelationshipWeek> relationships,
        List<NewsWeek> news,
        List<RecommendationWeek> recommendations,
        List<CallWeek> calls) {

    /**
     * Relationships the AI found that an analyst reviewed this week. {@code doubtsReviewed} are those the second check
     * doubted before the review, and {@code doubtsRejected} the ones of them the analyst then rejected.
     */
    public record RelationshipWeek(LocalDate weekStart, LocalDate weekEnd, int reviewed, int approved, int rejected,
                                   int doubtsReviewed, int doubtsRejected) {
    }

    /**
     * Articles an analyst decided this week, and how many of them now differ from the AI's judgement. Noise made events
     * and events made noise are part of {@code overruled}; the rest of it are changes of event type or severity.
     */
    public record NewsWeek(LocalDate weekStart, LocalDate weekEnd, int decided, int overruled, int noiseMadeEvents,
                           int eventsMadeNoise) {
    }

    /** Suggested next steps approved or withheld this week. */
    public record RecommendationWeek(LocalDate weekStart, LocalDate weekEnd, int decided, int approvedAsWritten,
                                     int edited, int withheld) {
    }

    /** Gemini calls started this week, by outcome, and their median duration; null when there were none. */
    public record CallWeek(LocalDate weekStart, LocalDate weekEnd, int answered, int unreadable, int failed,
                           Integer medianMs) {
    }
}
