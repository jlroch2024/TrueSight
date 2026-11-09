package com.truesight.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AiCallRepository extends JpaRepository<AiCall, Long>, JpaSpecificationExecutor<AiCall> {

    /** Newest first, as the audit trail shows them. */
    List<AiCall> findTop100ByOrderByStartedAtDesc();

    /**
     * A call is complete once an answer has been read and its kept and dropped counts have been saved, including
     * zeroes. Relationship finding and the criticality pass both use this to avoid asking again.
     */
    boolean existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
            Long reportId, AiPurpose purpose, AiOutcome outcome);

    /** The completed calls of this purpose for a report: what ReportReading reads their markers from. */
    List<AiCall> findByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(Long reportId, AiPurpose purpose, AiOutcome outcome);

    /** Whether any call of this purpose was ever recorded for the report, answered or not. */
    boolean existsByReportIdAndPurpose(Long reportId, AiPurpose purpose);

    /** True when an answered call for this report used a prompt that contains the given text. */
    boolean existsByReportIdAndPurposeAndOutcomeAndPromptContaining(
            Long reportId, AiPurpose purpose, AiOutcome outcome, String promptText);

    /**
     * How many calls for these purposes started at or after {@code since}, whatever their outcome and whoever made
     * them: a failed call may have cost money too. The daily limit on reading reports counts with this.
     */
    long countByPurposeInAndStartedAtGreaterThanEqual(Collection<AiPurpose> purposes, Instant since);

    /**
     * When each call in {@code [from, to)} started, how it ended and how long it took, for the AI Quality Dashboard.
     * Only those three columns are read: prompts and answers can be very long.
     */
    @Query("""
            select new com.truesight.ai.AiCallTiming(c.startedAt, c.outcome, c.durationMs)
            from AiCall c
            where c.startedAt >= :from and c.startedAt < :to
            """)
    List<AiCallTiming> findTimingsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
