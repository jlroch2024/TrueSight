package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Creates, renames, deletes and lists the logged-in user's portfolios.
 *
 * <p>Every lookup goes through {@link PortfolioRepository#findByIdAndUserId}, so another user's portfolio is "not
 * found" (404), exactly like one that does not exist. Nobody can find out which portfolios exist by trying numbers.
 */
@Service
public class PortfolioService {

    static final String NAME_IN_USE = "You already have a portfolio with this name.";
    static final String NOT_FOUND = "Portfolio not found.";
    static final String SAMPLE_NAME = "Sample Portfolio";
    /** A copy of docs/examples/holdings.csv, since the built backend cannot read docs/. A test keeps them the same. */
    static final String SAMPLE_HOLDINGS = "/sample-holdings.csv";

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CurrentUser currentUser;
    private final HoldingCsvReader csvReader;
    private final AnalysisService analysis;
    private final AnalysisJob job;
    private final TransactionTemplate transaction;

    public PortfolioService(PortfolioRepository portfolios, HoldingRepository holdings, CurrentUser currentUser,
                            HoldingCsvReader csvReader, AnalysisService analysis, AnalysisJob job,
                            PlatformTransactionManager transactions) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
        this.csvReader = csvReader;
        this.analysis = analysis;
        this.job = job;
        this.transaction = new TransactionTemplate(transactions);
    }

    public List<PortfolioResponse> list() {
        return portfolios.findByUserIdOrderByNameAsc(currentUser.id()).stream()
                .map(PortfolioResponse::from)
                .toList();
    }

    @Transactional
    public PortfolioResponse create(String name) {
        Long userId = currentUser.id();
        String trimmed = name.trim();
        if (portfolios.existsByUserIdAndName(userId, trimmed)) {
            throw ApiException.conflict(NAME_IN_USE);
        }
        return PortfolioResponse.from(portfolios.saveAndFlush(new Portfolio(userId, trimmed)));
    }

    /**
     * Creates "Sample Portfolio" (or "Sample Portfolio 2", and so on, when the name is taken) with the sample CSV's
     * holdings, then starts their analysis as Analyse does. The portfolio and its holdings are saved in one transaction
     * that ends before the analysis starts, so the background job always finds them. It is an ordinary portfolio.
     */
    public PortfolioResponse createSample() {
        Long userId = currentUser.id();
        List<HoldingCsvReader.Row> rows = sampleHoldings();
        Portfolio portfolio = transaction.execute(status -> {
            Portfolio saved = portfolios.saveAndFlush(new Portfolio(userId, freeSampleName(userId)));
            holdings.saveAll(rows.stream().map(row -> new Holding(saved.getId(), row.ticker(), row.weight())).toList());
            return saved;
        });
        List<Long> queued = analysis.start(portfolio.getId());
        if (!queued.isEmpty()) job.run(queued);
        return PortfolioResponse.from(portfolio);
    }

    private String freeSampleName(Long userId) {
        String name = SAMPLE_NAME;
        for (int number = 2; portfolios.existsByUserIdAndName(userId, name); number++) {
            name = SAMPLE_NAME + " " + number;
        }
        return name;
    }

    private List<HoldingCsvReader.Row> sampleHoldings() {
        try (InputStream in = PortfolioService.class.getResourceAsStream(SAMPLE_HOLDINGS)) {
            if (in == null) throw new IllegalStateException("The sample holdings are missing from the build.");
            return csvReader.read(in);
        } catch (IOException exception) {
            throw new IllegalStateException("The sample holdings could not be read.", exception);
        }
    }

    @Transactional
    public PortfolioResponse rename(Long id, String name) {
        Long userId = currentUser.id();
        Portfolio portfolio = find(id, userId);
        String trimmed = name.trim();
        // Renaming a portfolio to its own name changes nothing, so it is not a clash.
        if (!trimmed.equals(portfolio.getName()) && portfolios.existsByUserIdAndName(userId, trimmed)) {
            throw ApiException.conflict(NAME_IN_USE);
        }
        portfolio.setName(trimmed);
        return PortfolioResponse.from(portfolio);
    }

    /** Removes the portfolio and its holdings. Relationships belong to the reports, so they stay. */
    @Transactional
    public void delete(Long id) {
        Portfolio portfolio = find(id, currentUser.id());
        holdings.deleteByPortfolioId(portfolio.getId());
        portfolios.delete(portfolio);
    }

    private Portfolio find(Long id, Long userId) {
        return portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
