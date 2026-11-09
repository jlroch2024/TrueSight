package com.truesight.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Reads and saves the running servers. */
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    /** Removes the servers not seen since {@code cutoff}, so the table does not grow with every deploy. */
    @Transactional
    @Modifying
    @Query("delete from Worker worker where worker.lastSeenAt < :cutoff")
    int deleteSeenBefore(@Param("cutoff") Instant cutoff);
}
