package com.truesight.relationship;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A relationship an analyst found in a company's stored annual report. */
public record AddRelationshipRequest(
        @NotNull Long companyId,
        @NotBlank @Size(max = 300) String counterpartyName,
        @NotNull RelationshipType type,
        @Size(max = 500) String provides,
        @NotBlank String sentence) {
}
