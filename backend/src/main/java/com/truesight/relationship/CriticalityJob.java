package com.truesight.relationship;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs the stored-report criticality pass away from the request thread. */
@Component
public class CriticalityJob {

    private static final Logger log = LoggerFactory.getLogger(CriticalityJob.class);
    private final CriticalityPassService pass;

    public CriticalityJob(CriticalityPassService pass) {
        this.pass = pass;
    }

    @Async
    public void run(List<Long> reportIds) {
        for (Long reportId : reportIds) {
            try {
                pass.assess(reportId);
            } catch (RuntimeException exception) {
                // One report failing must not prevent the remaining stored reports from being assessed.
                log.warn("Criticality assessment failed for stored report {}.", reportId, exception);
            }
        }
    }
}
