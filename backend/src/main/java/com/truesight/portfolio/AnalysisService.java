package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.config.WorkRecovery;
import com.truesight.config.WorkerHeartbeat;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportReader;
import com.truesight.report.ReportRepository;
import com.truesight.report.ReportTextExtractor;
import com.truesight.report.SecClient;
import com.truesight.report.SupplierReader;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CurrentUser currentUser;
    private final CompanyRepository companyRepository;
    private final ReportRepository reports;
    private final RelationshipRepository relationships;
    private final AiCallRepository aiCalls;
    private final ReportReader reportReader;
    private final SupplierReader suppliers;
    private final WorkRecovery recovery;
    private final WorkerHeartbeat heartbeat;

    public AnalysisService(PortfolioRepository portfolios, HoldingRepository holdings, CurrentUser currentUser,
                          CompanyRepository companyRepository, ReportRepository reports,
                          RelationshipRepository relationships, AiCallRepository aiCalls, ReportReader reportReader,
                          SupplierReader suppliers, WorkRecovery recovery, WorkerHeartbeat heartbeat) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.companyRepository = companyRepository;
        this.reports = reports;
        this.relationships = relationships;
        this.aiCalls = aiCalls;
        this.reportReader = reportReader;
        this.suppliers = suppliers;
        this.recovery = recovery;
        this.heartbeat = heartbeat;
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
        // First: work whose server has gone is Failed (and committed) before the 409 check below reads the rows.
        recovery.recover(portfolioId);
        requirePortfolio(portfolioId);
        List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        if (rows.stream().anyMatch(h -> h.getStatus() == HoldingStatus.ANALYSING)) {
            throw ApiException.conflict("Analysis is already running for this portfolio.");
        }
        List<Holding> toAnalyse = rows.stream().filter(h -> switch (h.getStatus()) {
            case WAITING, FAILED -> true;
            case DONE -> needsRelationshipAnalysis(h) || needsFigures(h);
            default -> false;
        }).toList();
        // setStatus also clears a failed holding's old reason.
        toAnalyse.forEach(h -> {
            h.setStatus(HoldingStatus.ANALYSING);
            h.setWorkerId(heartbeat.id());
        });
        holdings.saveAll(toAnalyse);
        return toAnalyse.stream().map(Holding::getId).toList();
    }

    /** A Done holding whose report's dependency figures were never read: its next Analyse reads only those. */
    /**
     * Sets Waiting the unread suppliers of the portfolio's Done holdings that are not being analysed now (those are
     * queued with their holding's own analysis), and returns their company ids: a supplier whose server stopped
     * before it was read has no status again, so a Done holding's analysis is not repeated just to read it. Nothing
     * is sent to the AI here.
     */
    @Transactional
    public List<Long> restartSuppliers(Long portfolioId, List<Long> queuedHoldingIds) {
        requirePortfolio(portfolioId);
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                .filter(h -> h.getStatus() == HoldingStatus.DONE && h.getCompanyId() != null)
                .filter(h -> !queuedHoldingIds.contains(h.getId()))
                .flatMap(h -> suppliers.queue(h.getCompanyId()).stream())
                .distinct()
                .toList();
    }

    private boolean needsFigures(Holding holding) {
        if (holding.getCompanyId() == null) return false;
        return reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId())
                .map(report -> report.getFiguresReadAt() == null)
                .orElse(false);
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
            Company company = reportReader.companyFor(holding.getTicker());
            if (company == null) {
                holding.setStatus(HoldingStatus.NO_REPORT_FOUND);
                return;
            }
            holding.setCompanyId(company.getId());
            if (!reportReader.read(company)) {
                holding.setStatus(HoldingStatus.NO_REPORT_FOUND);
                return;
            }
            holding.setStatus(HoldingStatus.DONE);
            // Its suppliers wait from the moment it is Done; AnalysisJob reads them afterwards.
            suppliers.queue(company.getId());
        } catch (SecClient.SecAccessException | GeminiClient.GeminiException exception) {
            holding.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            holding.fail("The annual report could not be processed.");
        }
    }

    /**
     * The report whose relationships were just saved for this holding, once the holding is Done: the one its second
     * check is run for. Null for a holding that is not Done, or that has no company or report.
     */
    @Transactional(readOnly = true)
    public Long reportToSecondCheck(Long holdingId) {
        Holding holding = holdings.findById(holdingId).orElse(null);
        if (holding == null || holding.getStatus() != HoldingStatus.DONE || holding.getCompanyId() == null) return null;
        return reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId()).map(Report::getId).orElse(null);
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
