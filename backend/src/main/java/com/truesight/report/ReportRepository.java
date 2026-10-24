package com.truesight.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Reads and saves annual reports. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByAccessionNumber(String accessionNumber);

    /** The company's newest saved report, if any. */
    Optional<Report> findFirstByCompanyIdOrderByFilingDateDesc(Long companyId);

    @Query("select distinct r.companyId from Report r")
    List<Long> findCompanyIdsWithAReport();

    @Transactional
    void deleteByCompanyId(Long companyId);
}
