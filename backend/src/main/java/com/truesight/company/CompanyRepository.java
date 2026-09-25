package com.truesight.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Reads and saves companies. */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCik(String cik);
}
