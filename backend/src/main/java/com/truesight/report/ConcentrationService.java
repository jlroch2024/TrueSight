package com.truesight.report;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.RelationshipResponse.CompanySummary;
import com.truesight.relationship.RelationshipResponse.ReportSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Serves the dependency figures stored for a company's reports. */
@Service
public class ConcentrationService {

    private final CompanyRepository companies;
    private final ConcentrationRepository concentrations;
    private final ReportRepository reports;
    private final CompanyDisplayNames displayNames;

    public ConcentrationService(CompanyRepository companies, ConcentrationRepository concentrations,
                                ReportRepository reports, CompanyDisplayNames displayNames) {
        this.companies = companies;
        this.concentrations = concentrations;
        this.reports = reports;
        this.displayNames = displayNames;
    }

    /** The company's figures, newest period first. Companies are shared, so any signed-in user may ask. */
    @Transactional(readOnly = true)
    public List<ConcentrationResponse> forCompany(Long companyId) {
        if (!companies.existsById(companyId)) {
            throw ApiException.notFound("Company not found.");
        }
        List<Concentration> rows = concentrations.findByCompanyIdOrderByPeriodEndDescPeriodStartDescIdAsc(companyId);

        Set<Long> counterpartyIds = rows.stream().map(Concentration::getCounterpartyId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, CompanySummary> counterparties = new HashMap<>();
        for (Company company : companies.findAllById(counterpartyIds)) {
            counterparties.put(company.getId(), new CompanySummary(company.getId(), company.getName(),
                    displayNames.of(company), company.getTicker()));
        }
        Map<Long, ReportSummary> reportOf = new HashMap<>();
        reports.findInfoByCompanyIdIn(List.of(companyId)).forEach(info ->
                reportOf.put(info.getId(), new ReportSummary(info.getForm(), info.getFilingDate(), info.getUrl())));

        return rows.stream().map(row -> new ConcentrationResponse(row.getLabel(),
                row.getCounterpartyId() == null ? null : counterparties.get(row.getCounterpartyId()),
                row.getKind(), row.getMeasure(), row.getShare(), row.getPeriodStart(), row.getPeriodEnd(),
                reportOf.get(row.getReportId()))).toList();
    }
}
