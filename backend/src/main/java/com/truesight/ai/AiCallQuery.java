package com.truesight.ai;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Optional filters shared by the AI call list and its CSV export. Dates are whole UTC days. */
public record AiCallQuery(AiPurpose purpose, AiOutcome outcome, LocalDate from, LocalDate to) {

    public Specification<AiCall> toSpecification() {
        Specification<AiCall> specification = Specification.unrestricted();
        if (purpose != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("purpose"), purpose));
        }
        if (outcome != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("outcome"), outcome));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("startedAt"), from.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("startedAt"), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        return specification;
    }
}
