package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipFinder;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.ReportTextExtractor;
import com.truesight.report.SecClient;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CurrentUser currentUser;
    private final CompanyDirectory companies;
    private final CompanyRepository companyRepository;
    private final ReportRepository reports;
    private final RelationshipRepository relationships;
    private final AiCallRepository aiCalls;
    private final SecClient sec;
    private final RelationshipFinder relationshipFinder;

    public AnalysisService(PortfolioRepository portfolios, HoldingRepository holdings, CurrentUser currentUser,
                          CompanyDirectory companies, CompanyRepository companyRepository,
                          ReportRepository reports, RelationshipRepository relationships, AiCallRepository aiCalls,
                          SecClient sec, RelationshipFinder relationshipFinder) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.companies = companies;
        this.companyRepository = companyRepository;
        this.reports = reports;
        this.relationships = relationships;
        this.aiCalls = aiCalls;
        this.sec = sec;
        this.relationshipFinder = relationshipFinder;
    }

    /**
     * Starts analysing the portfolio's holdings that are Waiting, and tries again those that Failed: a failure is
     * usually the SEC or the AI being briefly unreachable, so pressing Analyse again should retry it rather than
     * leave the portfolio stuck. Holdings already Done or with No Report Found are left alone. Refused only while an
     * analysis is already running for this portfolio. A completed holding with no saved relationships is retried only
     * when its latest report has no completed relationship-finding answer, or its text matches the legacy extraction
     * format and can be refreshed from the saved SEC filing first.
     */
    @Transactional
    public List<Long> start(Long portfolioId) {
        requirePortfolio(portfolioId);
        List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        if (rows.stream().anyMatch(h -> h.getStatus() == HoldingStatus.ANALYSING)) {
            throw ApiException.conflict("Analysis is already running for this portfolio.");
        }
        List<Holding> toAnalyse = rows.stream().filter(h -> switch (h.getStatus()) {
            case WAITING, FAILED -> true;
            case DONE -> needsRelationshipAnalysis(h);
            default -> false;
        }).toList();
        // setStatus also clears a failed holding's old reason.
        toAnalyse.forEach(h -> h.setStatus(HoldingStatus.ANALYSING));
        holdings.saveAll(toAnalyse);
        return toAnalyse.stream().map(Holding::getId).toList();
    }

    private boolean needsRelationshipAnalysis(Holding holding) {
        if (holding.getCompanyId() == null) return false;
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId()).orElse(null);
        if (report == null || relationships.existsByReportId(report.getId())) return false;
        if (ReportTextExtractor.hasLegacyWrappedParagraphs(report.getText())) return true;
        return !aiCalls.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
                report.getId(), AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED);
    }

    @Transactional(readOnly = true)
    public List<HoldingView> getHoldings(Long portfolioId) {
        requirePortfolio(portfolioId);
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream().map(holding -> {
            String name = holding.getCompanyId() == null ? holding.getTicker() : companyRepository.findById(holding.getCompanyId())
                    .map(Company::getName).orElse(holding.getTicker());
            Report report = holding.getCompanyId() == null ? null : reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId()).orElse(null);
            return new HoldingView(holding.getId(), holding.getTicker(), name, holding.getStatus().name(),
                    holding.getStatusReason(), report == null ? null : report.getForm(),
                    report == null ? null : report.getFilingDate(), report == null ? null : report.getUrl());
        }).toList();
    }

    @Transactional
    public void analyze(Long holdingId) {
        Holding holding = holdings.findById(holdingId).orElse(null);
        if (holding == null) return;
        if (holding.getStatus() != HoldingStatus.ANALYSING) return;
        try {
            SecClient.SecCompany secCompany = sec.companyForTicker(holding.getTicker());
            if (secCompany == null) {
                holding.setStatus(HoldingStatus.NO_REPORT_FOUND);
                return;
            }
            Company company = companies.findOrCreate(secCompany.name(), secCompany.cik(), secCompany.ticker(), List.of());
            holding.setCompanyId(company.getId());
            Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId()).orElse(null);
            if (report == null) {
                SecClient.SecFiling filing = sec.latestAnnualFiling(secCompany.cik());
                if (filing == null) {
                    holding.setStatus(HoldingStatus.NO_REPORT_FOUND);
                    return;
                }
                String text = ReportTextExtractor.extract(sec.download(filing.url()));
                report = reports.save(new Report(company.getId(), filing.form(), filing.accessionNumber(), filing.filingDate(), filing.url(), text));
            }
            boolean refreshed = false;
            if (ReportTextExtractor.hasLegacyWrappedParagraphs(report.getText())) {
                String currentText = ReportTextExtractor.extract(sec.download(report.getUrl()));
                if (!currentText.isBlank() && !currentText.equals(report.getText())) {
                    report.setText(currentText);
                    reports.save(report);
                    refreshed = true;
                }
            }
            // A changed report gets one new assessment; unchanged answered reports remain idempotent.
            relationshipFinder.findIn(report, company, refreshed);
            holding.setStatus(HoldingStatus.DONE);
        } catch (SecClient.SecAccessException | GeminiClient.GeminiException exception) {
            holding.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            holding.fail("The annual report could not be processed.");
        }
    }

    /**
     * The latest report of a holding that is Done, or null if the holding is not Done. The second check of its
     * relationships is asked for by this report.
     */
    @Transactional(readOnly = true)
    public Long reportIdOfDoneHolding(Long holdingId) {
        return holdings.findById(holdingId)
                .filter(holding -> holding.getStatus() == HoldingStatus.DONE && holding.getCompanyId() != null)
                .flatMap(holding -> reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId()))
                .map(Report::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String tickerFor(Long holdingId) {
        return holdings.findById(holdingId).map(Holding::getTicker).orElse(holdingId.toString());
    }

    private void requirePortfolio(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
    }

    public record HoldingView(Long id, String ticker, String companyName, String status, String statusReason,
                              String reportType, java.time.LocalDate filingDate, String reportUrl) { }
}
