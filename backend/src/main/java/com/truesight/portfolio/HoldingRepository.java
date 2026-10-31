package com.truesight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Reads and saves holdings. Callers check the portfolio belongs to the logged-in user first, through
 * {@link PortfolioRepository#findByIdAndUserId}.
 */
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPortfolioIdOrderByTickerAsc(Long portfolioId);

    List<Holding> findByPortfolioIdAndStatusOrderByTickerAsc(Long portfolioId, HoldingStatus status);

    /** The portfolios that hold at least one analysed company, oldest first. */
    @Query("select distinct holding.portfolioId from Holding holding "
            + "where holding.status = :status and holding.companyId is not null order by holding.portfolioId")
    List<Long> findPortfolioIdsWithStatus(@Param("status") HoldingStatus status);

    /** One holding, only if it is in this portfolio. */
    Optional<Holding> findByIdAndPortfolioId(Long id, Long portfolioId);

    /** Whether the portfolio already holds this ticker, whatever its capitals. */
    boolean existsByPortfolioIdAndTickerIgnoreCase(Long portfolioId, String ticker);

    long countByPortfolioId(Long portfolioId);

    void deleteByPortfolioId(Long portfolioId);
}
