package com.truesight.relationship;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lists the relationships for a portfolio's holdings. Relationships belong to reports, not portfolios, so these are
 * the relationships found in the reports of the companies the portfolio holds.
 */
@Service
public class RelationshipService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final ReportRepository reports;
    private final CurrentUser currentUser;

    public RelationshipService(PortfolioRepository portfolios, HoldingRepository holdings,
                               RelationshipRepository relationships, CompanyRepository companies,
                               ReportRepository reports, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<RelationshipResponse> forPortfolio(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));

        Set<Long> held = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                .map(Holding::getCompanyId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (held.isEmpty()) {
            return List.of();
        }
        List<Relationship> found = relationships.findByCompanyIdIn(held).stream()
                .sorted(Comparator.comparing(Relationship::getId)).toList();

        Set<Long> companyIds = new HashSet<>();
        found.forEach(r -> {
            companyIds.add(r.getCompanyId());
            companyIds.add(r.getCounterpartyId());
        });
        Map<Long, Company> companyById = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, Report> reportById = reports.findAllById(found.stream().map(Relationship::getReportId).toList())
                .stream().collect(Collectors.toMap(Report::getId, Function.identity()));

        return found.stream().map(r -> {
            Report report = reportById.get(r.getReportId());
            return new RelationshipResponse(
                    r.getId(),
                    summary(companyById.get(r.getCompanyId())),
                    summary(companyById.get(r.getCounterpartyId())),
                    r.getType(),
                    r.getProvides(),
                    r.getEvidence(),
                    new RelationshipResponse.ReportSummary(report.getForm(), report.getFilingDate(), report.getUrl()));
        }).toList();
    }

    private static RelationshipResponse.CompanySummary summary(Company company) {
        return new RelationshipResponse.CompanySummary(company.getId(), company.getName(), company.getTicker());
    }
}
