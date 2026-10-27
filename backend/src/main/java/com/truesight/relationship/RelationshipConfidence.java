package com.truesight.relationship;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * How confident TrueSight is about a relationship, High, Medium or Low, and why. Worked out here and nowhere else, from
 * three facts (the See How Confident TrueSight Is story); the website shows it and never works it out itself.
 *
 * <ul>
 *   <li><b>The review:</b> an analyst approved it, or rejected it.</li>
 *   <li><b>The quote match:</b> the share of the AI's quote found, in order, in the report. Strong at 95% or more,
 *       partial below (the quote checker's floor is 85%). Relationships found before the share was recorded have
 *       none.</li>
 *   <li><b>The second check:</b> a second AI agrees or doubts.</li>
 * </ul>
 *
 * <p>The rule: approved is High, and rejected is Low: an analyst's decision outweighs the other two facts. Otherwise, a
 * second-check doubt is Low; a strong match with the second check agreeing is High; anything else is Medium. Every fact
 * is listed as a reason, with the effect it had on the level.
 *
 * <p>Rejected relationships never reach a graph, and new alerts leave them out, but an alert made before the rejection
 * still lists its path, so a rejected link must never read as confident.
 */
public record RelationshipConfidence(Level level, List<Reason> reasons) {

    /** A quote match at least this strong, with the second check agreeing, is High. */
    static final BigDecimal STRONG_MATCH = new BigDecimal("0.95");

    public enum Level {
        LOW,
        MEDIUM,
        HIGH
    }

    /** What a reason did to the level. */
    public enum Effect {
        RAISES,
        LOWERS,
        NONE
    }

    /** One fact, as a user reads it ("Strong Quote Match (97%)"), and its effect. */
    public record Reason(String text, Effect effect) {
    }

    public static RelationshipConfidence of(Relationship relationship) {
        boolean approved = relationship.getReviewStatus() == ReviewStatus.APPROVED;
        boolean rejected = relationship.getReviewStatus() == ReviewStatus.REJECTED;
        BigDecimal match = relationship.getQuoteMatch();
        boolean strongMatch = match != null && match.compareTo(STRONG_MATCH) >= 0;
        SecondCheckVerdict secondCheck = relationship.getSecondCheck();

        Level level;
        Effect reviewEffect = Effect.NONE;
        Effect matchEffect = Effect.NONE;
        Effect secondCheckEffect = Effect.NONE;
        if (approved) {
            level = Level.HIGH;
            reviewEffect = Effect.RAISES;
        } else if (rejected) {
            level = Level.LOW;
            reviewEffect = Effect.LOWERS;
        } else if (secondCheck == SecondCheckVerdict.DOUBTS) {
            level = Level.LOW;
            secondCheckEffect = Effect.LOWERS;
        } else if (strongMatch && secondCheck == SecondCheckVerdict.AGREES) {
            level = Level.HIGH;
            matchEffect = Effect.RAISES;
            secondCheckEffect = Effect.RAISES;
        } else {
            level = Level.MEDIUM;
        }

        return new RelationshipConfidence(level, List.of(
                new Reason(review(relationship.getReviewStatus()), reviewEffect),
                new Reason(quoteMatch(match, strongMatch), matchEffect),
                new Reason(secondCheck(secondCheck, relationship.getSecondCheckReason()), secondCheckEffect)));
    }

    private static String review(ReviewStatus status) {
        return switch (status) {
            case APPROVED -> "An Analyst Approved It";
            case REJECTED -> "An Analyst Rejected It";
            case UNREVIEWED -> "Not Yet Reviewed";
        };
    }

    /** The share as a whole percentage, rounded down, so 94.9% never reads "95%" beside "Partial". */
    private static String quoteMatch(BigDecimal match, boolean strong) {
        if (match == null) return "Quote Match Not Recorded";
        int percent = match.movePointRight(2).setScale(0, RoundingMode.DOWN).intValue();
        return "%s Quote Match (%d%%)".formatted(strong ? "Strong" : "Partial", percent);
    }

    private static String secondCheck(SecondCheckVerdict verdict, String reason) {
        if (verdict == null) return "Not Yet Second-Checked";
        if (verdict == SecondCheckVerdict.AGREES) return "Second Check Agrees";
        return reason == null || reason.isBlank() ? "Second Check Doubts" : "Second Check Doubts: " + reason;
    }
}
