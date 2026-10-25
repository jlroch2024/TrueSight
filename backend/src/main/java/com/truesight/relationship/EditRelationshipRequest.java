package com.truesight.relationship;

import jakarta.validation.constraints.Size;

/** The part of a relationship an analyst may correct. Evidence always remains the report's own words. */
public record EditRelationshipRequest(
        @Size(max = 500) String provides) {
}
