package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and saves relationships from older annual reports. Only the SEC Filing Timeline uses it. */
public interface PastRelationshipRepository extends JpaRepository<PastRelationship, Long> {

    /** Every older relationship from these companies' reports. */
    List<PastRelationship> findByCompanyIdIn(Collection<Long> companyIds);

    List<PastRelationship> findByReportIdOrderByIdAsc(Long reportId);
}
