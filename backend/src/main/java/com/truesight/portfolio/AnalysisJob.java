package com.truesight.portfolio;

import com.truesight.relationship.SecondCheckPassService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs the portfolio work away from the request thread. A holding is Done as soon as its relationships are saved; its
 * second check (a second AI reading each claim) runs afterwards, here, and can never change the holding.
 */
@Component
public class AnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJob.class);

    private final AnalysisService analysis;
    private final SecondCheckPassService secondChecks;
    private final Executor analysisExecutor;
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> reportLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis, SecondCheckPassService secondChecks,
                       @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.analysis = analysis;
        this.secondChecks = secondChecks;
        this.analysisExecutor = analysisExecutor;
    }

    @Async
    public void run(List<Long> holdingIds) {
        CompletableFuture<?>[] tasks = holdingIds.stream()
                .map(holdingId -> CompletableFuture.runAsync(() -> analyze(holdingId), analysisExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
    }

    private void analyze(Long holdingId) {
        String ticker = analysis.tickerFor(holdingId);
        synchronized (companyLocks.computeIfAbsent(ticker, ignored -> new Object())) {
            analysis.analyze(holdingId);
        }
        // analysis.analyze has committed the holding as Done (or not), so the holding no longer waits for the second
        // check. The check still runs on this analysis worker, so while every worker is busy a holding that has not
        // started yet starts a little later; there is no ordering between that and a second check.
        secondCheck(holdingId);
    }

    /**
     * Has the second AI check the relationships saved for this holding's report that have no check yet. It never fails
     * the holding: a relationship whose check fails stays saved with none, and the analyst-run pass asks again.
     */
    private void secondCheck(Long holdingId) {
        try {
            Long reportId = analysis.reportToSecondCheck(holdingId);
            if (reportId == null) return;
            // Two holdings of one company share a report: the second to arrive finds nothing left to check.
            synchronized (reportLocks.computeIfAbsent(reportId, ignored -> new Object())) {
                secondChecks.check(reportId);
            }
        } catch (RuntimeException failure) {
            log.warn("The second check for holding {} could not be made. The analyst-run pass will ask again.",
                    holdingId, failure);
        }
    }
}
