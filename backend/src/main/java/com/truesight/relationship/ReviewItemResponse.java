package com.truesight.relationship;

import java.time.Instant;
import java.util.List;

/**
 * One relationship on the Review page: the relationship itself, its review, and how many portfolios rely on it.
 *
 * <p>{@code reviewedBy} is the display name (or, with none set, the email) of the Analyst who decided last, and {@code reviewedAt} when. All
 * three review fields are null while the relationship is {@code UNREVIEWED}. {@code usedByPortfolios} is only a count:
 * it never says which portfolios, or whose.
 *
 * <p>{@code secondCheck} is what the second AI made of the relationship, AGREES or DOUBTS, and {@code secondCheckReason}
 * its reason. Both are null until the relationship has been second-checked.
 *
 * <p>{@code highlights} are the words that matter in the evidence, empty when there are none.
 */
public record ReviewItemResponse(
        RelationshipResponse relationship,
        ReviewStatus reviewStatus,
        String reviewNote,
        String reviewedBy,
        Instant reviewedAt,
        long usedByPortfolios,
        SecondCheckVerdict secondCheck,
        String secondCheckReason,
        List<RelationshipResponse.Highlight> highlights) {
}
