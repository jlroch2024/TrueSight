package com.truesight.portfolio;

import com.truesight.relationship.HighlightJob;
import com.truesight.report.SupplierReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Runs the portfolio work away from the request thread. */
@Component
public class AnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJob.class);

    private final AnalysisService analysis;
    private final Executor analysisExecutor;
    private final HighlightJob highlights;
    private final SupplierReader suppliers;
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis, @Qualifier("analysisExecutor") Executor analysisExecutor,
                       HighlightJob highlights, SupplierReader suppliers) {
        this.analysis = analysis;
        this.analysisExecutor = analysisExecutor;
        this.highlights = highlights;
        this.suppliers = suppliers;
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
        synchronized (lockFor(ticker)) {
            analysis.analyze(holdingId);
        }
        // Started here, once the holding's relationships are saved, so the job can see them.
        highlights.run();
        // The holding is Done already: its suppliers are read afterwards, behind the work queued before them.
        List<Long> waiting = suppliers.waitingFor(holdingId);
        if (!waiting.isEmpty()) {
            analysisExecutor.execute(() -> readSuppliers(waiting));
        }
    }

    /** Reads the suppliers one by one. One that fails is recorded as Failed and the rest are still read. */
    private void readSuppliers(List<Long> companyIds) {
        for (Long companyId : companyIds) {
            String ticker = suppliers.tickerOf(companyId);
            // Locked like a holding of the same ticker, so the same company is never read twice at once.
            synchronized (lockFor(ticker == null ? "company-" + companyId : ticker)) {
                try {
                    suppliers.read(companyId);
                } catch (RuntimeException exception) {
                    log.warn("Reading supplier {}'s annual report failed.", companyId, exception);
                    suppliers.fail(companyId);
                }
            }
        }
        highlights.run();
    }

    private Object lockFor(String ticker) {
        return companyLocks.computeIfAbsent(ticker.toUpperCase(Locale.ROOT), ignored -> new Object());
    }
}
