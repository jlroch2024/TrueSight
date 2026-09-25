package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and saves relationships. */
public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    /** Every relationship from these companies' reports, e.g. all the holdings in one portfolio. */
    List<Relationship> findByCompanyIdIn(Collection<Long> companyIds);

    boolean existsByReportId(Long reportId);
}
