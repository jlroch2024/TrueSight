package com.truesight.relationship;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads every quote not yet read for highlights, batch by batch, away from the request thread.
 *
 * <p>Only one pass runs at a time, or holdings finishing together would each send the same quotes to Gemini. Asked while a
 * pass is running, the job goes round again when it finishes, so nothing saved in the meantime waits for the next request.
 * Each pass takes each quote once: quotes whose answer could not be read wait for the next pass.
 */
@Component
public class HighlightJob {

    private static final Logger log = LoggerFactory.getLogger(HighlightJob.class);

    private final RelationshipRepository relationships;
    private final HighlightFinder finder;
    private final HighlightSaver saver;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean asked = new AtomicBoolean();

    public HighlightJob(RelationshipRepository relationships, HighlightFinder finder, HighlightSaver saver) {
        this.relationships = relationships;
        this.finder = finder;
        this.saver = saver;
    }

    @Async
    public void run() {
        asked.set(true);
        // The outer loop catches a request made just as the running pass was stopping.
        while (asked.get() && running.compareAndSet(false, true)) {
            try {
                while (asked.getAndSet(false)) {
                    pass();
                }
            } finally {
                running.set(false);
            }
        }
    }

    private void pass() {
        List<Relationship> pending = relationships.findByHighlightedAtIsNullOrderByIdAsc();
        for (int start = 0; start < pending.size(); start += HighlightFinder.BATCH_SIZE) {
            List<Relationship> batch = pending.subList(start, Math.min(start + HighlightFinder.BATCH_SIZE, pending.size()));
            try {
                Map<Long, List<RelationshipHighlight>> found = finder.find(batch);
                if (!found.isEmpty()) {
                    saver.save(found);
                }
            } catch (GeminiClient.GeminiException exception) {
                // The next batch would fail the same way. The quotes stay plain, and the next pass reads them.
                log.warn("Reading quotes for highlights stopped; the next pass reads them.", exception);
                return;
            } catch (RuntimeException exception) {
                // One batch failing must not stop the others from being read.
                log.warn("Reading {} quote(s) for highlights failed; the next pass reads them.", batch.size(), exception);
            }
        }
    }
}
