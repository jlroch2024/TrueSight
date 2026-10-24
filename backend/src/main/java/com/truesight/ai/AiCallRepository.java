package com.truesight.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    /** Newest first, as the audit trail shows them. */
    List<AiCall> findTop100ByOrderByStartedAtDesc();

    /** An answered criticality pass is complete once its kept/dropped counts have been saved, including zeroes. */
    boolean existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
            Long reportId, AiPurpose purpose, AiOutcome outcome);

    boolean existsByReportIdAndPurposeAndOutcomeAndPromptContaining(
            Long reportId, AiPurpose purpose, AiOutcome outcome, String promptText);
}
