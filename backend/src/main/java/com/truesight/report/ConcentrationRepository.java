package com.truesight.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and saves the dependency figures of annual reports. */
public interface ConcentrationRepository extends JpaRepository<Concentration, Long> {

    List<Concentration> findByReportIdOrderByIdAsc(Long reportId);

    /** The company's figures, newest period first. */
    List<Concentration> findByCompanyIdOrderByPeriodEndDescPeriodStartDescIdAsc(Long companyId);

    /** Every figure from these companies' reports. */
    List<Concentration> findByCompanyIdIn(Collection<Long> companyIds);

    /** Every figure that names one of these companies. */
    List<Concentration> findByCounterpartyIdIn(Collection<Long> counterpartyIds);
}
