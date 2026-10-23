package com.truesight.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    /** Newest first, as the audit trail shows them. */
    List<AiCall> findTop100ByOrderByStartedAtDesc();

    /** A report is complete once an answer has been read and its kept/dropped counts have been saved. */
    boolean existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
            Long reportId, AiPurpose purpose, AiOutcome outcome);
}
