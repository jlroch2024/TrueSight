package com.truesight.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Reads and saves companies. */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCik(String cik);

    /** The companies with this stock ticker, ignoring capitals. A ticker is not unique: it may be wrong, or reused. */
    List<Company> findByTickerIgnoreCase(String ticker);

    /** The companies that do not file with the SEC: the ones found in other companies' reports. */
    List<Company> findByCikIsNullOrderByIdAsc();
}
