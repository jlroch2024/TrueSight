package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Reads and saves relationships. */
public interface RelationshipRepository extends JpaRepository<Relationship, Long> {

    /** Every relationship from these companies' reports, e.g. all the holdings in one portfolio. */
    List<Relationship> findByCompanyIdIn(Collection<Long> companyIds);

    /** Every relationship touching these companies, from their own reports or naming them as the counterparty. */
    List<Relationship> findByCompanyIdInOrCounterpartyIdIn(Collection<Long> companyIds,
                                                           Collection<Long> counterpartyIds);

    boolean existsByReportId(Long reportId);

    @Query("select distinct r.reportId from Relationship r where r.type = :type and r.criticality is null")
    List<Long> findReportIdsWithoutCriticality(@Param("type") RelationshipType type);

    List<Relationship> findByReportIdOrderByIdAsc(Long reportId);

    List<Relationship> findByReviewStatus(ReviewStatus reviewStatus);

    boolean existsByCompanyIdAndCounterpartyIdAndTypeAndEvidence(
            Long companyId, Long counterpartyId, RelationshipType type, String evidence);

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
