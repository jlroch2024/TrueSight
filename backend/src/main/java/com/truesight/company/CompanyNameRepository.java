package com.truesight.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Reads and saves the names companies have been seen under. */
public interface CompanyNameRepository extends JpaRepository<CompanyName, Long> {

    Optional<CompanyName> findByNameKey(String nameKey);

    boolean existsByNameKey(String nameKey);
}
