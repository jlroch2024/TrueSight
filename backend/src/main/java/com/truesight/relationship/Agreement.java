package com.truesight.relationship;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds when both companies' own reports state the same relationship: one is in A's report, naming B, and the other is
 * in B's report, naming A, the other way round (one says supplier, the other customer). Both must have been found in
 * reports, not added by an analyst, and neither Rejected.
 */
@Component
public class Agreement {

    private final RelationshipRepository relationships;

    public Agreement(RelationshipRepository relationships) {
        this.relationships = relationships;
    }

    /** For each of these relationships that another report agrees with, its id and the one that agrees with it. */
    public Map<Long, Relationship> of(Collection<Relationship> found) {
        List<Relationship> eligible = found.stream().filter(Agreement::counts).toList();
        Map<Long, Relationship> agreeing = new HashMap<>();
        if (eligible.isEmpty()) return agreeing;
        Set<Long> companyIds = eligible.stream().map(Relationship::getCompanyId).collect(Collectors.toSet());
        Set<Long> counterpartyIds = eligible.stream().map(Relationship::getCounterpartyId).collect(Collectors.toSet());
        // Every relationship whose company is one of their counterparties, and whose counterparty is one of their companies.
        Map<String, Relationship> byClaim = relationships.findByCompanyIdInAndCounterpartyIdIn(counterpartyIds, companyIds)
                .stream()
                .filter(Agreement::counts)
                .sorted(Comparator.comparing(Relationship::getId))
                .collect(Collectors.toMap(Agreement::claim, other -> other, (first, later) -> first));
        for (Relationship relationship : eligible) {
            Relationship other = byClaim.get(mirrorOf(relationship));
            if (other != null && !Objects.equals(other.getReportId(), relationship.getReportId())) {
                agreeing.put(relationship.getId(), other);
            }
        }
        return agreeing;
    }

    private static boolean counts(Relationship relationship) {
        return relationship.getSource() != RelationshipSource.ANALYST
                && relationship.getReviewStatus() != ReviewStatus.REJECTED;
    }

    private static String claim(Relationship relationship) {
        return relationship.getCompanyId() + " " + relationship.getCounterpartyId() + " " + relationship.getType();
    }

    /** The claim the other company's report would make: its company and counterparty swapped, and the opposite type. */
    private static String mirrorOf(Relationship relationship) {
        RelationshipType opposite = relationship.getType() == RelationshipType.SUPPLIER
                ? RelationshipType.CUSTOMER : RelationshipType.SUPPLIER;
        return relationship.getCounterpartyId() + " " + relationship.getCompanyId() + " " + opposite;
    }
}
