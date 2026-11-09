package com.truesight.portfolio;

import com.truesight.relationship.HighlightJob;
import com.truesight.relationship.NameSearchJob;
import com.truesight.relationship.SecondCheckPassService;
import com.truesight.report.FigureService;
import com.truesight.report.SupplierReader;
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
    private final HighlightJob highlights;
    private final SupplierReader suppliers;
    private final FigureService figures;
    private final NameSearchJob nameSearches;
    private final ConcurrentHashMap<Long, Object> reportLocks = new ConcurrentHashMap<>();

    public AnalysisJob(AnalysisService analysis, SecondCheckPassService secondChecks,
                       @Qualifier("analysisExecutor") Executor analysisExecutor,
                       HighlightJob highlights, SupplierReader suppliers, FigureService figures,
                       NameSearchJob nameSearches) {
        this.analysis = analysis;
        this.secondChecks = secondChecks;
        this.analysisExecutor = analysisExecutor;
        this.highlights = highlights;
        this.suppliers = suppliers;
        this.figures = figures;
        this.nameSearches = nameSearches;
    }

    @Async
    public void run(List<Long> holdingIds) {
        CompletableFuture<?>[] tasks = holdingIds.stream()
                .map(holdingId -> CompletableFuture.runAsync(() -> analyze(holdingId), analysisExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
    }

    /**
     * Starts the name search of each of these holdings that are Done already, in the background. Each runs only if its
     * company is due one: never searched, searched over 90 days ago, or its last search failed.
     */
    public void searchNames(List<Long> doneHoldingIds) {
        doneHoldingIds.forEach(nameSearches::run);
    }

    /** Reads these suppliers, whose server stopped before they were read (see {@link AnalysisService#restartSuppliers}). */
    @Async
    public void runSuppliers(List<Long> companyIds) {
        readSuppliers(companyIds);
    }

    private void analyze(Long holdingId) {
        // One company is never analysed twice at once: analyze takes the company's DatabaseLock.
        analysis.analyze(holdingId);
        // Started here, once the holding's relationships are saved, so the job can see them.
        highlights.run();
        // The holding is Done already: its suppliers are read afterwards, behind the work queued before them.
        List<Long> waiting = suppliers.waitingFor(holdingId);
        if (!waiting.isEmpty()) {
            analysisExecutor.execute(() -> readSuppliers(waiting));
        }
        // And the other companies' reports that name it, likewise afterwards (if the holding is Done).
        nameSearches.run(holdingId);
        // The holding's dependency figures, once the work queued above is started. The holding's own transaction has
        // committed, and the figures have one of their own: a failure here loses only the figures.
        Long companyId = analysis.companyIdFor(holdingId);
        if (companyId != null) figures.readLatestOf(companyId);
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

    /** Reads the suppliers one by one. One that fails is recorded as Failed and the rest are still read. */
    private void readSuppliers(List<Long> companyIds) {
        for (Long companyId : companyIds) {
            try {
                suppliers.read(companyId);
                figures.readLatestOf(companyId);
            } catch (RuntimeException exception) {
                log.warn("Reading supplier {}'s annual report failed.", companyId, exception);
                suppliers.fail(companyId);
            }
        }
        highlights.run();
    }
}
