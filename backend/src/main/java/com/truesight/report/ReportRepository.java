package com.truesight.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Reads and saves annual reports. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByAccessionNumber(String accessionNumber);

    /** The company's newest saved report, if any. */
    Optional<Report> findFirstByCompanyIdOrderByFilingDateDesc(Long companyId);

    @Transactional
    void deleteByCompanyId(Long companyId);
}
