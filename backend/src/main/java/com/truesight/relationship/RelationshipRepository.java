package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/** Reads and saves relationships. */
public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    /** Every relationship from these companies' reports, e.g. all the holdings in one portfolio. */
    List<Relationship> findByCompanyIdIn(Collection<Long> companyIds);

    boolean existsByReportId(Long reportId);

    List<Relationship> findByReviewStatus(ReviewStatus reviewStatus);

    /**
     * For every relationship, how many distinct portfolios hold either of its two companies: rows of
     * {@code [relationshipId, portfolios]}. One query for the whole list. A relationship nobody holds has no row.
     */
    @Query("""
            SELECT r.id, COUNT(DISTINCT h.portfolioId)
            FROM Relationship r, Holding h
            WHERE h.companyId = r.companyId OR h.companyId = r.counterpartyId
            GROUP BY r.id
            """)
    List<Object[]> countPortfoliosUsingEach();
}
