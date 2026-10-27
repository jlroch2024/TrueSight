package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.news.AlertRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Lists a portfolio's holdings, replaces them from an uploaded CSV, and adds, re-weights or removes one.
 *
 * <p>Every method first checks the portfolio belongs to the logged-in user; another user's portfolio is "not found" (404).
 */
@Service
public class HoldingService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CompanyRepository companies;
    private final CompanyDisplayNames displayNames;
    private final ReportRepository reports;
    private final HoldingCsvReader csvReader;
    private final CurrentUser currentUser;
    private final AlertRepository alerts;

    /** The longest ticker the holdings table holds; the CSV reader refuses longer ones too. */
    private static final int MAX_TICKER_LENGTH = 20;
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100");

    public HoldingService(PortfolioRepository portfolios, HoldingRepository holdings, CompanyRepository companies,
                          CompanyDisplayNames displayNames, ReportRepository reports, HoldingCsvReader csvReader, CurrentUser currentUser,
                          AlertRepository alerts) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.companies = companies;
        this.displayNames = displayNames;
        this.reports = reports;
        this.csvReader = csvReader;
        this.currentUser = currentUser;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> list(Long portfolioId) {
        checkOwned(portfolioId);
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream().map(this::toResponse).toList();
    }

    /**
     * Replaces every holding in the portfolio with the file's. The file is read in full before anything is deleted,
     * and it all happens in one transaction, so a bad file leaves the old holdings as they were.
     */
    @Transactional
    public List<HoldingResponse> replace(Long portfolioId, MultipartFile file) {
        checkOwned(portfolioId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a CSV file to upload.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) {
            throw ApiException.badRequest("The file must be a CSV.");
        }

        List<HoldingCsvReader.Row> rows;
        try (InputStream in = file.getInputStream()) {
            rows = csvReader.read(in);
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }

        holdings.deleteByPortfolioId(portfolioId);
        holdings.flush();
        holdings.saveAll(rows.stream().map(row -> new Holding(portfolioId, row.ticker(), row.weight())).toList());
        return list(portfolioId);
    }

    /**
     * Adds one holding, saved as Waiting. The ticker is made upper-case; one the portfolio already holds is refused.
     * Starting its analysis is the controller's job, after this transaction has been saved.
     */
    @Transactional
    public HoldingResponse add(Long portfolioId, AddHoldingRequest request) {
        checkOwned(portfolioId);
        String ticker = request.ticker() == null ? "" : request.ticker().trim().toUpperCase(Locale.ROOT);
        if (ticker.isEmpty()) {
            throw ApiException.badRequest("Enter a ticker.");
        }
        if (ticker.length() > MAX_TICKER_LENGTH) {
            throw ApiException.badRequest("\"" + ticker + "\" is too long to be a ticker.");
        }
        BigDecimal weight = checkedWeight(request.weight());
        if (holdings.existsByPortfolioIdAndTickerIgnoreCase(portfolioId, ticker)) {
            throw ApiException.conflict(ticker + " is already in this portfolio.");
        }
        return toResponse(holdings.save(new Holding(portfolioId, ticker, weight)));
    }

    /** Changes one holding's weight; an empty weight says it has none. */
    @Transactional
    public HoldingResponse changeWeight(Long portfolioId, Long holdingId, ChangeWeightRequest request) {
        checkOwned(portfolioId);
        Holding holding = findHolding(portfolioId, holdingId);
        holding.setWeight(checkedWeight(request.weight()));
        return toResponse(holdings.save(holding));
    }

    /**
     * Removes one holding. The database removes its places in the portfolio's alerts, and an alert left with no
     * holdings is deleted too, all in this one transaction.
     */
    @Transactional
    public void remove(Long portfolioId, Long holdingId) {
        checkOwned(portfolioId);
        holdings.delete(findHolding(portfolioId, holdingId));
        holdings.flush();
        alerts.deleteWithoutHoldings(portfolioId);
    }

    private Holding findHolding(Long portfolioId, Long holdingId) {
        return holdings.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> ApiException.notFound("Holding not found."));
    }

    /** A weight is a percentage from 0 to 100, or empty. */
    private static BigDecimal checkedWeight(BigDecimal weight) {
        if (weight != null && (weight.signum() < 0 || weight.compareTo(MAX_WEIGHT) > 0)) {
            throw ApiException.badRequest("The weight must be between 0 and 100.");
        }
        return weight;
    }

    private void checkOwned(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
    }

    private HoldingResponse toResponse(Holding holding) {
        Company company = holding.getCompanyId() == null ? null
                : companies.findById(holding.getCompanyId()).orElse(null);
        Report report = holding.getCompanyId() == null ? null
                : reports.findFirstByCompanyIdOrderByFilingDateDesc(holding.getCompanyId()).orElse(null);
        return new HoldingResponse(
                holding.getId(),
                holding.getTicker(),
                holding.getWeight(),
                company == null ? null : company.getName(),
                company == null ? null : displayNames.of(company),
                holding.getStatus(),
                holding.getStatusReason(),
                report == null ? null : report.getForm(),
                report == null ? null : report.getFilingDate(),
                report == null ? null : report.getUrl(),
                holding.getStatus() == HoldingStatus.DONE && report != null ? report.getCreatedAt() : null);
    }
}
