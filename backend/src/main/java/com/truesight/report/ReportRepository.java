package com.truesight.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Reads and saves annual reports. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByAccessionNumber(String accessionNumber);

    /** The company's newest saved report, if any. */
    Optional<Report> findFirstByCompanyIdOrderByFilingDateDesc(Long companyId);

    /** The id of the company's newest saved report, if any, without loading the report's text. */
    @Query("select r.id from Report r where r.companyId = :companyId order by r.filingDate desc limit 1")
    Optional<Long> findLatestIdByCompanyId(Long companyId);

    /**
     * These companies' reports as {@link ReportInfo}, which leaves out the text (megabytes a report), for the SEC
     * Filing Timeline, which needs only when each was filed and where.
     */
    List<ReportInfo> findInfoByCompanyIdIn(Collection<Long> companyIds);

    @Query("select distinct r.companyId from Report r")
    List<Long> findCompanyIdsWithAReport();

    @Transactional
    void deleteByCompanyId(Long companyId);
}
