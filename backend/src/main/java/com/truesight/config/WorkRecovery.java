package com.truesight.config;

import com.truesight.company.CompanyRepository;
import com.truesight.news.NewsCheckService;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.SupplierReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Finds work that a server restart interrupted, and reports it so the user can try again.
 *
 * <p>Work is alive while its server is: a server is alive when it has a {@code workers} row seen in the last 2
 * minutes, or when it is this server (a running server is alive by definition). Work with another owner, an owner
 * with no row, or no recorded owner at all (saved before the owner was recorded) has been interrupted:
 * <ul>
 *   <li>a holding on Analysing becomes Failed, with {@link Holding#INTERRUPTED_REASON};</li>
 *   <li>a portfolio on Checking becomes Failed, with {@link NewsCheckService#INTERRUPTED_REASON};</li>
 *   <li>a supplier on Waiting or Analysing goes back to no status, so the next Analyse reads it.</li>
 * </ul>
 * Each change is one conditional update, so two servers recovering at once cannot clash. Nothing carries on from where
 * it stopped: interrupted work is reported and tried again.
 */
@Component
public class WorkRecovery {

    private static final Logger log = LoggerFactory.getLogger(WorkRecovery.class);

    /** A server not seen for this long has gone. */
    static final Duration LIVE_FOR = Duration.ofMinutes(2);

    private final HoldingRepository holdings;
    private final PortfolioRepository portfolios;
    private final CompanyRepository companies;
    private final SupplierReader suppliers;
    private final WorkerHeartbeat heartbeat;
    private final boolean scheduled;

    public WorkRecovery(HoldingRepository holdings, PortfolioRepository portfolios, CompanyRepository companies,
                        SupplierReader suppliers, WorkerHeartbeat heartbeat,
                        @Value("${truesight.work.scheduled:true}") boolean scheduled) {
        this.holdings = holdings;
        this.portfolios = portfolios;
        this.companies = companies;
        this.suppliers = suppliers;
        this.heartbeat = heartbeat;
        this.scheduled = scheduled;
    }

    /** Every minute, each server looks for interrupted work everywhere. Does nothing when the schedule is off. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void scheduledRecovery() {
        if (!scheduled) return;
        try {
            recoverAll();
        } catch (RuntimeException failure) {
            log.warn("Looking for interrupted work failed; it will be tried again in a minute.", failure);
        }
    }

    /** Recovers every interrupted holding, news check and supplier. */
    @Transactional
    public void recoverAll() {
        Instant cutoff = Instant.now().minus(LIVE_FOR);
        int failedHoldings = holdings.failInterrupted(Holding.INTERRUPTED_REASON, heartbeat.id(), cutoff);
        int failedChecks = portfolios.failInterruptedChecks(NewsCheckService.INTERRUPTED_REASON, heartbeat.id(), cutoff);
        int resetSuppliers = companies.resetInterruptedSuppliers(heartbeat.id(), cutoff);
        report(failedHoldings, failedChecks, resetSuppliers);
    }

    /**
     * Recovers one portfolio's interrupted work, and its holdings' suppliers. Called first by Analyse and Check News,
     * so a button never refuses because of work that is gone. It commits on its own, before the caller reads the rows
     * and decides whether to answer 409.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(Long portfolioId) {
        Instant cutoff = Instant.now().minus(LIVE_FOR);
        int failedHoldings = holdings.failInterruptedIn(portfolioId, Holding.INTERRUPTED_REASON, heartbeat.id(), cutoff);
        int failedChecks = portfolios.failInterruptedCheckOf(portfolioId, NewsCheckService.INTERRUPTED_REASON,
                heartbeat.id(), cutoff);
        Set<Long> supplierIds = suppliers.suppliersOf(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                .map(Holding::getCompanyId).filter(id -> id != null).toList());
        int resetSuppliers = supplierIds.isEmpty() ? 0
                : companies.resetInterruptedSuppliersIn(supplierIds, heartbeat.id(), cutoff);
        report(failedHoldings, failedChecks, resetSuppliers);
    }

    private static void report(int failedHoldings, int failedChecks, int resetSuppliers) {
        if (failedHoldings + failedChecks + resetSuppliers > 0) {
            log.info("Recovered interrupted work: {} holding(s), {} news check(s), {} supplier(s).",
                    failedHoldings, failedChecks, resetSuppliers);
        }
    }
}
