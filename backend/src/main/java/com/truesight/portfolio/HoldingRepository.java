package com.truesight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves holdings. Callers check the portfolio belongs to the logged-in user first, through
 * {@link PortfolioRepository#findByIdAndUserId}.
 */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderByTickerAsc(Long portfolioId);

    List<Holding> findByPortfolioIdAndStatusOrderByTickerAsc(Long portfolioId, HoldingStatus status);

    /** One holding, only if it is in this portfolio. */
    Optional<Holding> findByIdAndPortfolioId(Long id, Long portfolioId);

    /** Whether the portfolio already holds this ticker, whatever its capitals. */
    boolean existsByPortfolioIdAndTickerIgnoreCase(Long portfolioId, String ticker);

    long countByPortfolioId(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);
}
