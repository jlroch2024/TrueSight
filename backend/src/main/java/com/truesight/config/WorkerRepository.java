package com.truesight.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Reads and saves the running servers. */
public interface WorkerRepository extends JpaRepository<Worker, UUID> {
}
