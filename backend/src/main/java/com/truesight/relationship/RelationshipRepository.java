package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** The relationships found in these reports. */
    List<Relationship> findByReportIdIn(Collection<Long> reportIds);

    @Query("select distinct r.reportId from Relationship r where r.type = :type and r.criticality is null")
    List<Long> findReportIdsWithoutCriticality(@Param("type") RelationshipType type);

    List<Relationship> findByReportIdOrderByIdAsc(Long reportId);

    /** The reports with at least one relationship the second AI has not checked yet, oldest first. */
    @Query("select distinct r.reportId from Relationship r where r.secondCheck is null order by r.reportId")
    List<Long> findReportIdsWithoutSecondCheck();

    List<Relationship> findByReportIdAndSecondCheckIsNullOrderByIdAsc(Long reportId);

    List<Relationship> findByReviewStatus(ReviewStatus reviewStatus);

    /**
     * The relationships the AI found that an analyst reviewed in {@code [from, to)}, for the AI Quality Dashboard. Ones
     * an analyst added are left out: they are approved as they are saved, so they say nothing about the AI.
     */
    @Query("""
            select r from Relationship r
            where r.source = com.truesight.relationship.RelationshipSource.AI
              and r.reviewedAt >= :from and r.reviewedAt < :to
            """)
    List<Relationship> findAiReviewedBetween(@Param("from") Instant from, @Param("to") Instant to);

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
