package com.truesight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * This server's identity, and the proof that it is alive. One random id is made when the server starts; every 30
 * seconds it saves a {@code workers} row with the time. Background work records {@link #id()}, so work whose server
 * has not been seen for 2 minutes (see {@link WorkRecovery}) is known to have been interrupted.
 */
@Component
public class WorkerHeartbeat {

    /** Rows older than this are removed: they belong to servers that were replaced long ago. */
    static final Duration KEEP_ROWS_FOR = Duration.ofDays(1);

    private final UUID id = UUID.randomUUID();
    private final WorkerRepository workers;
    private final boolean scheduled;

    public WorkerHeartbeat(WorkerRepository workers, @Value("${truesight.work.scheduled:true}") boolean scheduled) {
        this.workers = workers;
        this.scheduled = scheduled;
    }

    /** The id this server's work records. */
    public UUID id() {
        return id;
    }

    /** Renews this server's row and removes the old ones. Does nothing when {@code truesight.work.scheduled} is off. */
    @Scheduled(fixedRate = 30_000)
    public void beat() {
        if (!scheduled) return;
        Instant now = Instant.now();
        workers.save(new Worker(id, now));
        workers.deleteSeenBefore(now.minus(KEEP_ROWS_FOR));
    }
}
