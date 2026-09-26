package com.truesight.portfolio;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CurrentUser currentUser;

    public PortfolioService(PortfolioRepository portfolios, HoldingRepository holdings, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.currentUser = currentUser;
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
