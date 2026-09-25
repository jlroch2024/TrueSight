package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
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
    private final SecClient sec;

    public AnalysisService(PortfolioRepository portfolios, HoldingRepository holdings, CurrentUser currentUser,
                          CompanyDirectory companies, CompanyRepository companyRepository,
                          ReportRepository reports, SecClient sec) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.companies = companies;
        this.companyRepository = companyRepository;
        this.reports = reports;
        this.sec = sec;
    }

    @Transactional
    public List<Long> start(Long portfolioId) {
        requirePortfolio(portfolioId);
        List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        if (rows.stream().anyMatch(h -> h.getStatus() == HoldingStatus.FAILED)) {
            throw ApiException.conflict("Analysis cannot start while a holding has Failed status.");
        }
        if (rows.stream().anyMatch(h -> h.getStatus() == HoldingStatus.ANALYSING)) {
            throw ApiException.conflict("Analysis is already running for this portfolio.");
        }
        List<Long> queued = rows.stream().filter(h -> h.getStatus() == HoldingStatus.WAITING)
                .map(Holding::getId).toList();
        rows.stream().filter(h -> h.getStatus() == HoldingStatus.WAITING)
                .forEach(h -> h.setStatus(HoldingStatus.ANALYSING));
        holdings.saveAll(rows);
        return queued;
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
            if (reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId()).isPresent()) {
                holding.setStatus(HoldingStatus.DONE);
                return;
            }
            SecClient.SecFiling filing = sec.latestAnnualFiling(secCompany.cik());
            if (filing == null) {
                holding.setStatus(HoldingStatus.NO_REPORT_FOUND);
                return;
            }
            String text = ReportTextExtractor.extract(sec.download(filing.url()));
            reports.save(new Report(company.getId(), filing.form(), filing.accessionNumber(), filing.filingDate(), filing.url(), text));
            holding.setStatus(HoldingStatus.DONE);
        } catch (SecClient.SecAccessException exception) {
            holding.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            holding.fail("The annual report could not be processed.");
        }
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
