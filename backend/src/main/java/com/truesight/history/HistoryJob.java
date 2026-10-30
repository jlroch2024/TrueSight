package com.truesight.history;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Runs the reading of earlier annual reports away from the request thread. */
@Component
public class HistoryJob {

    private final HistoryReader reader;

    public HistoryJob(HistoryReader reader) {
        this.reader = reader;
    }

    @Async
    public void run(Long portfolioId) {
        reader.read(portfolioId);
    }
}
