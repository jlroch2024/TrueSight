package com.truesight.config;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A running server keeps its workers row less than a minute old, and rows older than a day are removed. The test
 * profile turns the schedule off for every other test; here it is on.
 */
@IntegrationTest
@TestPropertySource(properties = "truesight.work.scheduled=true")
class WorkerHeartbeatIT {

    @Autowired WorkerHeartbeat heartbeat;
    @Autowired WorkerRepository workers;

    private UUID oldRow;
    private UUID recentRow;

    @AfterEach
    void removeWhatTheTestSaved() {
        if (oldRow != null) workers.deleteById(oldRow);
        if (recentRow != null) workers.deleteById(recentRow);
    }

    @Test
    void aRunningServerKeepsItsRowLessThanAMinuteOld() {
        Instant before = Instant.now();

        heartbeat.beat();

        Worker row = workers.findById(heartbeat.id()).orElseThrow();
        assertThat(row.getLastSeenAt()).isAfterOrEqualTo(before.minusMillis(1));
        assertThat(Duration.between(row.getLastSeenAt(), Instant.now())).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void eachBeatRenewsTheSameRow() throws Exception {
        heartbeat.beat();
        Instant first = workers.findById(heartbeat.id()).orElseThrow().getLastSeenAt();
        Thread.sleep(20);

        heartbeat.beat();

        assertThat(workers.findById(heartbeat.id()).orElseThrow().getLastSeenAt()).isAfter(first);
        assertThat(workers.findAll()).filteredOn(worker -> worker.getId().equals(heartbeat.id())).hasSize(1);
    }

    @Test
    void rowsOlderThanADayAreRemovedAndNewerOnesKept() {
        oldRow = workers.save(new Worker(UUID.randomUUID(), Instant.now().minus(Duration.ofDays(1)).minusSeconds(60))).getId();
        recentRow = workers.save(new Worker(UUID.randomUUID(), Instant.now().minus(Duration.ofHours(23)))).getId();

        heartbeat.beat();

        assertThat(workers.existsById(oldRow)).isFalse();
        assertThat(workers.existsById(recentRow)).isTrue();
    }

    @Test
    void theServerHasOneIdForAsLongAsItRuns() {
        assertThat(heartbeat.id()).isNotNull().isEqualTo(heartbeat.id());
    }
}
