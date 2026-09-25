package com.truesight.portfolio;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Runs the portfolio work away from the request thread. */
@Component
public class AnalysisJob {

    private final AnalysisService analysis;
    private final ConcurrentHashMap<String, Object> companyLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @Async
    public void run(List<Long> holdingIds) {
        for (Long holdingId : holdingIds) {
            String ticker = analysis.tickerFor(holdingId);
            synchronized (companyLocks.computeIfAbsent(ticker, ignored -> new Object())) {
                analysis.analyze(holdingId);
            }
        }
    }
}
