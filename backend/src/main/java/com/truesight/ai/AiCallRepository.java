package com.truesight.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    /** Newest first, as the audit trail shows them. */
    List<AiCall> findTop100ByOrderByStartedAtDesc();

    /**
     * A call is complete once an answer has been read and its kept and dropped counts have been saved, including
     * zeroes. Relationship finding and the criticality pass both use this to avoid asking again.
     */
    boolean existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
            Long reportId, AiPurpose purpose, AiOutcome outcome);

    /** True when an answered call for this report used a prompt that contains the given text. */
    boolean existsByReportIdAndPurposeAndOutcomeAndPromptContaining(
            Long reportId, AiPurpose purpose, AiOutcome outcome, String promptText);
}
