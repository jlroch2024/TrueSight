package com.truesight.portfolio;

import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Runs the portfolio work away from the request thread. */
@Component
public class AnalysisJob {

    private final AnalysisService analysis;
    private final Executor analysisExecutor;
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis, @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.analysis = analysis;
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
    }
}
