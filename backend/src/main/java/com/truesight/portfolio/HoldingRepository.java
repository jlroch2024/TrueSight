package com.truesight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and saves holdings. Callers check the portfolio belongs to the logged-in user first, through
 * {@link PortfolioRepository#findByIdAndUserId}.
 */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderByTickerAsc(Long portfolioId);

    long countByPortfolioId(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);
}
