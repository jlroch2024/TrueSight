package com.truesight.relationship;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs the second-check pass away from the request thread. */
@Component
public class SecondCheckJob {

    private static final Logger log = LoggerFactory.getLogger(SecondCheckJob.class);
    private final SecondCheckPassService pass;

    public SecondCheckJob(SecondCheckPassService pass) {
        this.pass = pass;
    }

    @Async
    public void run(List<Long> reportIds) {
        for (Long reportId : reportIds) {
            try {
                pass.check(reportId);
            } catch (RuntimeException exception) {
                // One report failing must not stop the others from being checked.
                log.warn("The second check failed for report {}.", reportId, exception);
            }
        }
    }
}
