package com.truesight.relationship;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An Analyst's decision on one relationship: {@code APPROVED} or {@code REJECTED}. A rejection needs a {@code note}
 * saying why (checked in {@link ReviewService}, because it depends on the decision); an approval may have one.
 */
public record ReviewRequest(
        @NotNull ReviewStatus decision,
        @Size(max = 500) String note) {
}
