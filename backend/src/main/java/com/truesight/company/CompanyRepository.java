package com.truesight.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and saves companies. */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCik(String cik);

    /** The companies with this stock ticker, ignoring capitals. A ticker is not unique: it may be wrong, or reused. */
    List<Company> findByTickerIgnoreCase(String ticker);

    /** The companies that do not file with the SEC: the ones found in other companies' reports. */
    List<Company> findByCikIsNullOrderByIdAsc();

    /**
     * Puts every supplier waiting to be read, or being read, whose server has gone (see
     * {@code HoldingRepository.failInterrupted}) back to no status, so the next Analyse reads it. Returns how many it
     * changed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Company company set company.reportStatus = null, company.reportStatusReason = null "
            + "where company.reportStatus in (com.truesight.company.ReportStatus.WAITING, "
            + "com.truesight.company.ReportStatus.ANALYSING) "
            + "and (company.reportWorkerId is null or (company.reportWorkerId <> :self and company.reportWorkerId not in "
            + "(select worker.id from Worker worker where worker.lastSeenAt > :cutoff)))")
    int resetInterruptedSuppliers(@Param("self") UUID self, @Param("cutoff") Instant cutoff);

    /** {@link #resetInterruptedSuppliers} for these companies only. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Company company set company.reportStatus = null, company.reportStatusReason = null "
            + "where company.id in :ids and company.reportStatus in (com.truesight.company.ReportStatus.WAITING, "
            + "com.truesight.company.ReportStatus.ANALYSING) "
            + "and (company.reportWorkerId is null or (company.reportWorkerId <> :self and company.reportWorkerId not in "
            + "(select worker.id from Worker worker where worker.lastSeenAt > :cutoff)))")
    int resetInterruptedSuppliersIn(@Param("ids") Collection<Long> ids, @Param("self") UUID self,
                                    @Param("cutoff") Instant cutoff);
}
