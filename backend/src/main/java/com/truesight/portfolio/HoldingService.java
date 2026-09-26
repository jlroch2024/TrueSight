package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Lists a portfolio's holdings, and replaces them from an uploaded CSV.
 *
 * <p>Both first check the portfolio belongs to the logged-in user; another user's portfolio is "not found" (404).
 */
@Service
public class HoldingService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CompanyRepository companies;
    private final ReportRepository reports;
    private final HoldingCsvReader csvReader;
    private final CurrentUser currentUser;

    public HoldingService(PortfolioRepository portfolios, HoldingRepository holdings, CompanyRepository companies,
                          ReportRepository reports, HoldingCsvReader csvReader, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.companies = companies;
        this.reports = reports;
        this.csvReader = csvReader;
        this.currentUser = currentUser;
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
                holding.getStatus(),
                holding.getStatusReason(),
                report == null ? null : report.getForm(),
                report == null ? null : report.getFilingDate(),
                report == null ? null : report.getUrl());
    }
}
