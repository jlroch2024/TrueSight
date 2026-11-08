package com.truesight.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** Whether any portfolio holds this company. */
    boolean existsByCompanyId(Long companyId);

    void deleteByPortfolioId(Long portfolioId);

    /**
     * Marks every holding on Analysing whose server has gone Failed, with {@code reason}. A holding is interrupted when
     * it has no owner, or its owner is not {@code self} and has no {@code workers} row seen after {@code cutoff}. One
     * conditional update, so it only changes holdings still Analysing. Returns how many it changed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Holding holding set holding.status = com.truesight.portfolio.HoldingStatus.FAILED, "
            + "holding.statusReason = :reason "
            + "where holding.status = com.truesight.portfolio.HoldingStatus.ANALYSING "
            + "and (holding.workerId is null or (holding.workerId <> :self and holding.workerId not in "
            + "(select worker.id from Worker worker where worker.lastSeenAt > :cutoff)))")
    int failInterrupted(@Param("reason") String reason, @Param("self") UUID self, @Param("cutoff") Instant cutoff);

    /** {@link #failInterrupted} for one portfolio's holdings. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Holding holding set holding.status = com.truesight.portfolio.HoldingStatus.FAILED, "
            + "holding.statusReason = :reason "
            + "where holding.status = com.truesight.portfolio.HoldingStatus.ANALYSING "
            + "and holding.portfolioId = :portfolioId "
            + "and (holding.workerId is null or (holding.workerId <> :self and holding.workerId not in "
            + "(select worker.id from Worker worker where worker.lastSeenAt > :cutoff)))")
    int failInterruptedIn(@Param("portfolioId") Long portfolioId, @Param("reason") String reason,
                          @Param("self") UUID self, @Param("cutoff") Instant cutoff);
}
