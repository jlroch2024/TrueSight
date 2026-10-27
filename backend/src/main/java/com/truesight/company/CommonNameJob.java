package com.truesight.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Runs the common-names pass away from the request thread. */
@Component
public class CommonNameJob {

    private static final Logger log = LoggerFactory.getLogger(CommonNameJob.class);
    private final CommonNameService service;

    public CommonNameJob(CommonNameService service) {
        this.service = service;
    }

    @Async
    public void run() {
        try {
            service.run();
        } catch (RuntimeException exception) {
            log.warn("The common-names pass failed.", exception);
        }
    }
}
