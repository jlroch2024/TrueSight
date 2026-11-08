package com.truesight.relationship;

import com.truesight.report.SecClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Runs a holding's name search ({@link NameSearcher}) on the analysis executor, once the holding is Done, so it never
 * holds up the holding's status and never fails it.
 *
 * <ul>
 *   <li>A search the SEC cannot answer, or a filing it cannot send, is not recorded, so the next Analyse searches
 *       again.</li>
 *   <li>A filing the AI cannot read is skipped; the others are still read, and the search is recorded.</li>
 *   <li>Two holdings of one company never search at once: the second waits, then finds the search recorded.</li>
 * </ul>
 */
@Component
public class NameSearchJob {

    private static final Logger log = LoggerFactory.getLogger(NameSearchJob.class);

    private final NameSearcher searcher;
    private final SecondCheckPassService secondChecks;
    private final HighlightJob highlights;
    private final Executor analysisExecutor;
    private final ConcurrentHashMap<Long, Object> companyLocks = new ConcurrentHashMap<>();

    public NameSearchJob(NameSearcher searcher, SecondCheckPassService secondChecks, HighlightJob highlights,
                         @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.searcher = searcher;
        this.secondChecks = secondChecks;
        this.highlights = highlights;
        this.analysisExecutor = analysisExecutor;
    }

    /** Starts the holding's name search behind the work queued before it, if its company is due one. */
    public void run(Long holdingId) {
        analysisExecutor.execute(() -> search(holdingId));
    }

    private void search(Long holdingId) {
        try {
            NameSearcher.Holder holder = searcher.due(holdingId).orElse(null);
            if (holder == null) return;
            synchronized (companyLocks.computeIfAbsent(holder.companyId(), ignored -> new Object())) {
                // Searched for meanwhile, for a holding of the same company in another portfolio.
                if (searcher.due(holder.companyId(), Instant.now()).isEmpty()) return;
                NameSearcher.Filings filings = searcher.filingsFor(holder);
                for (SecClient.SecSearchHit hit : filings.toRead()) {
                    readOne(holder, hit);
                }
                searcher.record(holder.companyId(), filings.found(), Instant.now());
            }
            highlights.run();
        } catch (SecClient.SecAccessException exception) {
            log.warn("The name search for holding {} could not reach the SEC; the next Analyse searches again.",
                    holdingId, exception);
        } catch (RuntimeException exception) {
            log.warn("The name search for holding {} failed; the next Analyse searches again.", holdingId, exception);
        }
    }

    /** Reads one filing, then has the second AI check what it found. An AI failure skips only this filing. */
    private void readOne(NameSearcher.Holder holder, SecClient.SecSearchHit hit) {
        Long reportId;
        try {
            reportId = searcher.read(holder, hit);
        } catch (GeminiClient.GeminiException exception) {
            log.warn("The AI could not read filing {} for a name search; it is skipped.", hit.accessionNumber(),
                    exception);
            return;
        }
        if (reportId == null) return;
        try {
            secondChecks.check(reportId);
        } catch (RuntimeException exception) {
            // The relationships stay saved with no second check; the analyst-run pass asks again.
            log.warn("The second check for report {} could not be made.", reportId, exception);
        }
    }
}
