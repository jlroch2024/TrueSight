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
 * Runs the portfolio work away from the request thread. A holding is Done as soon as its relationships are saved; the
 * second check of those relationships is queued after it, and never changes the holding.
 */
@Component
public class AnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJob.class);

    private final AnalysisService analysis;
    private final SecondCheckPassService secondChecks;
    private final Executor analysisExecutor;
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis, SecondCheckPassService secondChecks,
                       @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.analysis = analysis;
        this.secondChecks = secondChecks;
        this.analysisExecutor = analysisExecutor;
    }

    @Async
    public void run(List<Long> holdingIds) {
        // The second check is queued behind the holdings still waiting, so it never delays another holding's analysis.
        CompletableFuture<?>[] tasks = holdingIds.stream()
                .map(holdingId -> CompletableFuture.runAsync(() -> analyze(holdingId), analysisExecutor)
                        .thenCompose(done -> CompletableFuture.runAsync(() -> secondCheck(holdingId), analysisExecutor)))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
    }

    private void analyze(Long holdingId) {
        String ticker = analysis.tickerFor(holdingId);
        synchronized (companyLocks.computeIfAbsent(ticker, ignored -> new Object())) {
            analysis.analyze(holdingId);
        }
    }

    /**
     * Has the second AI check the relationships of a holding that is already Done and has none yet. Whatever goes
     * wrong, the holding stays as it is, and the analyst-run pass asks again.
     */
    private void secondCheck(Long holdingId) {
        try {
            Long reportId = analysis.reportIdOfDoneHolding(holdingId);
            if (reportId != null) {
                secondChecks.check(reportId);
            }
        } catch (RuntimeException exception) {
            log.warn("The second check after analysing holding {} failed; the pass will ask again.", holdingId, exception);
        }
    }
}
