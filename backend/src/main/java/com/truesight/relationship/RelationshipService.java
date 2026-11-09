package com.truesight.relationship;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.news.NewsCheckService;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Concentration;
import com.truesight.report.ConcentrationMeasure;
import com.truesight.report.ConcentrationRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SupplierReader;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lists the relationships for a portfolio's holdings. Relationships belong to reports, not portfolios, so these are
 * the relationships found in the reports of the companies the portfolio holds, those in other companies' reports that
 * name a holding, and those that name who supplies the holdings' suppliers.
 */
@Service
public class RelationshipService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final RelationshipHighlightRepository highlights;
    private final CompanyRepository companies;
    private final ReportRepository reports;
    private final CompanyDisplayNames displayNames;
    private final CurrentUser currentUser;
    private final Agreement agreement;
    private final ConcentrationRepository concentrations;
    private final SupplierReader suppliers;

    public RelationshipService(PortfolioRepository portfolios, HoldingRepository holdings,
                               RelationshipRepository relationships, RelationshipHighlightRepository highlights,
                               CompanyRepository companies, ReportRepository reports,
                               CompanyDisplayNames displayNames,
                               CurrentUser currentUser, SupplierReader suppliers,
                               Agreement agreement,
                               ConcentrationRepository concentrations) {
        this.concentrations = concentrations;
        this.agreement = agreement;
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.highlights = highlights;
        this.companies = companies;
        this.reports = reports;
        this.displayNames = displayNames;
        this.currentUser = currentUser;
        this.suppliers = suppliers;
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
        // The suppliers' own suppliers, two steps from a holding: the relationships in which a holding's supplier is the
        // customer. A supplier's other customers do not reach the portfolio, so they are left out.
        Set<Long> heldSuppliers = suppliers.suppliersOf(held);
        Map<Long, Relationship> byId = new LinkedHashMap<>();
        relationships.findByCompanyIdIn(held).forEach(r -> byId.put(r.getId(), r));
        // Other companies' reports that name a holding as their supplier or customer, such as a name search finds.
        relationships.findByCounterpartyIdIn(held).forEach(r -> byId.putIfAbsent(r.getId(), r));
        if (!heldSuppliers.isEmpty()) {
            relationships.findByCompanyIdInOrCounterpartyIdIn(heldSuppliers, heldSuppliers).stream()
                    .filter(r -> heldSuppliers.contains(NewsCheckService.customerId(r)))
                    .forEach(r -> byId.putIfAbsent(r.getId(), r));
        }
        // A rejected relationship is wrong, so it never reaches a graph. It is not deleted: a rejection can be undone.
        List<Relationship> found = byId.values().stream()
                .filter(r -> r.getReviewStatus() != ReviewStatus.REJECTED)
                .sorted(Comparator.comparing(Relationship::getId)).toList();
        return toResponses(found);
    }

    /** Turns relationships into what the website sees, looking up their companies, reports and highlights in one go each. */
    @Transactional(readOnly = true)
    public List<RelationshipResponse> toResponses(List<Relationship> found) {
        Set<Long> companyIds = new HashSet<>();
        found.forEach(r -> {
            companyIds.add(r.getCompanyId());
            companyIds.add(r.getCounterpartyId());
        });
        Map<Long, Company> companyById = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, Relationship> agreeing = agreement.of(found);
        Map<Long, Report> reportById = reports.findAllById(java.util.stream.Stream.concat(found.stream(), agreeing.values().stream())
                        .map(Relationship::getReportId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Report::getId, Function.identity()));
        Map<Long, List<RelationshipResponse.Highlight>> highlightsById = highlights
                .findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(found.stream().map(Relationship::getId).toList())
                .stream().collect(Collectors.groupingBy(RelationshipHighlight::getRelationshipId, Collectors.mapping(
                        h -> new RelationshipResponse.Highlight(h.getKind(), h.getStartAt(), h.getEndAt()),
                        Collectors.toList())));

        Map<String, Concentration> figures = latestFigures(found);

        return found.stream().map(r -> {
            Report report = reportById.get(r.getReportId());
            Relationship other = agreeing.get(r.getId());
            return new RelationshipResponse(
                    r.getId(),
                    summary(companyById.get(r.getCompanyId())),
                    summary(companyById.get(r.getCounterpartyId())),
                    r.getType(),
                    r.getProvides(),
                    r.getEvidence(),
                    r.getSource(),
                    r.getFoundBy(),
                    r.getCriticality(),
                    r.getCriticalityEvidence(),
                    summaryOf(report),
                    r.getReviewStatus(),
                    r.getReviewedAt(),
                    r.getReviewNote(),
                    RelationshipConfidence.of(r, other != null),
                    highlightsById.getOrDefault(r.getId(), List.of()),
                    other == null ? null : new RelationshipResponse.AgreesWith(other.getId(), other.getEvidence(),
                            summaryOf(reportById.get(other.getReportId()))),
                    figure(figures.get(pairKey(r.getReportId(), r.getCounterpartyId(), r.getType().name()))));
        }).toList();
    }

    /** For each report, counterparty and kind: the latest revenue or purchases figure, revenue first. One query. */
    private Map<String, Concentration> latestFigures(List<Relationship> found) {
        Map<String, Concentration> latest = new HashMap<>();
        if (found.isEmpty()) return latest;
        Comparator<Concentration> better = Comparator.comparing(Concentration::getPeriodEnd)
                .thenComparing(figure -> figure.getMeasure() == ConcentrationMeasure.REVENUE)
                .thenComparing(Concentration::getShare);
        concentrations.findByReportIdIn(found.stream().map(Relationship::getReportId).collect(Collectors.toSet())).stream()
                .filter(figure -> figure.getCounterpartyId() != null)
                .filter(figure -> figure.getMeasure() == ConcentrationMeasure.REVENUE
                        || figure.getMeasure() == ConcentrationMeasure.PURCHASES)
                .forEach(figure -> latest.merge(pairKey(figure.getReportId(), figure.getCounterpartyId(), figure.getKind().name()),
                        figure, (one, other) -> better.compare(one, other) >= 0 ? one : other));
        return latest;
    }

    private static String pairKey(Long reportId, Long counterpartyId, String kind) {
        return reportId + " " + counterpartyId + " " + kind;
    }

    private static RelationshipResponse.Figure figure(Concentration figure) {
        return figure == null ? null
                : new RelationshipResponse.Figure(figure.getShare(), figure.getMeasure(), figure.getPeriodEnd());
    }

    private static RelationshipResponse.ReportSummary summaryOf(Report report) {
        return new RelationshipResponse.ReportSummary(report.getForm(), report.getFilingDate(), report.getUrl());
    }

    private RelationshipResponse.CompanySummary summary(Company company) {
        return new RelationshipResponse.CompanySummary(company.getId(), company.getName(), displayNames.of(company),
                company.getTicker());
    }
}
