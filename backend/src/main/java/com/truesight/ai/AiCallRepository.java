package com.truesight.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    /** Newest first, as the audit trail shows them. */
    List<AiCall> findTop100ByOrderByStartedAtDesc();
}
