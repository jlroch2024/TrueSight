package com.truesight.history;

import com.truesight.common.ApiException;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.PastRelationship;
import com.truesight.relationship.PastRelationshipRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.ReviewStatus;
import com.truesight.report.ReportInfo;
import com.truesight.report.ReportRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The SEC Filing Timeline: reads a portfolio's holdings' earlier annual reports on request, and shows how the
 * suppliers and customers they name changed year by year.
 *
 * <p>The latest report's relationships are the ones in the graph (those not Rejected); each earlier report's are its
 * past relationships. Only reports already read are shown, so a year is never judged from a report nobody has read.
 */
@Service
public class HistoryService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final PastRelationshipRepository pastRelationships;
    private final HistoryReads reads;
    private final HistoryProgress progress;
    private final HistoryJob job;
    private final CompanyRepository companyRecords;
    private final CompanyDisplayNames displayNames;
    private final CurrentUser currentUser;
    private final CompanyNameRepository companyNames;
    private final ReportRepository reports;

    HistoryService(PortfolioRepository portfolios, HoldingRepository holdings, RelationshipRepository relationships,
                   PastRelationshipRepository pastRelationships, HistoryReads reads, HistoryProgress progress,
                   HistoryJob job, CompanyRepository companyRecords, CompanyDisplayNames displayNames,
                   CurrentUser currentUser, CompanyNameRepository companyNames, ReportRepository reports) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.pastRelationships = pastRelationships;
        this.reads = reads;
        this.progress = progress;
        this.job = job;
        this.companyRecords = companyRecords;
        this.displayNames = displayNames;
        this.currentUser = currentUser;
        this.companyNames = companyNames;
        this.reports = reports;
    }

    /** Starts reading every holding's missing earlier reports in the background. */
    public void start(Long portfolioId) {
        requirePortfolio(portfolioId);
        int unread = unreadOf(portfolioId);
        if (unread == 0) return;
        if (!progress.begin(portfolioId, unread)) {
            throw ApiException.conflict("Earlier reports are already being read for this portfolio.");
        }
        job.run(portfolioId);
    }

    @Transactional(readOnly = true)
    public HistoryResponse get(Long portfolioId) {
        requirePortfolio(portfolioId);
        Set<Long> companyIds = analysedCompanies(portfolioId);
        Map<Long, List<ReportInfo>> byCompany = reads.reportsByCompany(companyIds);

        int unread = 0;
        int unreadHoldings = 0;
        List<ReportInfo> shown = new ArrayList<>();
        Set<Long> latestIds = new LinkedHashSet<>();
        Set<Long> earlierIds = new LinkedHashSet<>();
        for (Long companyId : companyIds) {
            List<ReportInfo> saved = byCompany.getOrDefault(companyId, List.of());
            if (saved.isEmpty()) continue;
            int left = reads.unread(companyId, saved);
            unread += left;
            if (left > 0) unreadHoldings++;
            shown.add(saved.get(0));
            latestIds.add(saved.get(0).getId());
            HistoryReads.earlier(saved).stream().filter(report -> reads.isRead(report.getId())).forEach(report -> {
                shown.add(report);
                earlierIds.add(report.getId());
            });
        }

        Map<Long, List<HistoryTimeline.Edge>> edgesByReport = new HashMap<>();
        // An analyst's rejection of a link in a report is a decision: it is never carried as merely not listed.
        Set<String> rejected = new HashSet<>();
        for (Relationship relationship : relationships.findByReportIdIn(latestIds)) {
            if (relationship.getReviewStatus() == ReviewStatus.REJECTED) {
                rejected.add(relationship.getReportId() + "-" + relationship.getCounterpartyId());
                continue;
            }
            edgesByReport.computeIfAbsent(relationship.getReportId(), id -> new ArrayList<>())
                    .add(new HistoryTimeline.Edge(relationship.getCounterpartyId(), relationship.getType(),
                            relationship.getProvides(), relationship.getEvidence()));
        }
        for (PastRelationship past : pastRelationships.findByReportIdIn(earlierIds)) {
            edgesByReport.computeIfAbsent(past.getReportId(), id -> new ArrayList<>())
                    .add(new HistoryTimeline.Edge(past.getCounterpartyId(), past.getType(), past.getProvides(),
                            past.getEvidence()));
        }
        List<HistoryTimeline.Source> sources = shown.stream()
                .map(report -> new HistoryTimeline.Source(report.getId(), report.getCompanyId(), report.getFilingDate(),
                        report.getUrl(), edgesByReport.getOrDefault(report.getId(), List.of())))
                .toList();

        List<HistoryResponse.Year> years = HistoryTimeline.years(sources,
                (source, companyId) -> !rejected.contains(source.reportId() + "-" + companyId)
                        && reportNames(source.reportId(), companyId));
        Set<Long> named = new LinkedHashSet<>();
        years.forEach(year -> year.relationships().forEach(entry -> {
            named.add(entry.companyId());
            named.add(entry.counterpartyId());
        }));
        List<HistoryResponse.Company> companies = companyRecords.findAllById(named).stream()
                .map(company -> new HistoryResponse.Company(company.getId(), company.getName(),
                        displayNames.of(company), company.getTicker()))
                .toList();

        HistoryProgress.State state = progress.of(portfolioId);
        return new HistoryResponse(state.status(), state.reason(),
                state.status() == HistoryStatus.IDLE ? null : new HistoryResponse.Progress(state.done(), state.total()),
                unread, unreadHoldings, companyIds.size(), years, companies);
    }

    /** Whether the report's own text has any name the company is known by, as whole words. */
    private boolean reportNames(Long reportId, Long companyId) {
        Set<String> names = new LinkedHashSet<>();
        companyRecords.findById(companyId).ifPresent(company -> {
            names.add(company.getName());
            names.add(displayNames.of(company));
        });
        companyNames.findByCompanyId(companyId).forEach(name -> names.add(name.getName()));
        String pattern = names.stream().filter(name -> name != null && !name.isBlank())
                .map(HistoryService::wholeWords).collect(Collectors.joining("|"));
        return !pattern.isEmpty() && reports.textMatches(reportId, pattern);
    }

    /** A name as a PostgreSQL pattern: its punctuation taken literally, any spacing, and whole words only. */
    static String wholeWords(String name) {
        String literal = name.strip().replaceAll("([^\\p{L}\\p{N}\\s])", "\\\\$1").replaceAll("\\s+", "\\\\s+");
        return "\\y" + literal + "\\y";
    }

    private int unreadOf(Long portfolioId) {
        Set<Long> companyIds = analysedCompanies(portfolioId);
        Map<Long, List<ReportInfo>> byCompany = reads.reportsByCompany(companyIds);
        return companyIds.stream().mapToInt(id -> reads.unread(id, byCompany.getOrDefault(id, List.of()))).sum();
    }

    /** The companies of the portfolio's analysed holdings, once each. */
    private Set<Long> analysedCompanies(Long portfolioId) {
        Set<Long> companyIds = new LinkedHashSet<>();
        for (Holding holding : holdings.findByPortfolioIdOrderByTickerAsc(portfolioId)) {
            if (holding.getCompanyId() != null && holding.getStatus() == HoldingStatus.DONE) {
                companyIds.add(holding.getCompanyId());
            }
        }
        return companyIds;
    }

    private void requirePortfolio(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
    }
}
