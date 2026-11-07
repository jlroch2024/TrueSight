package com.truesight.history;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where reading each portfolio's earlier reports has got. Held in memory, because it is only about work happening now;
 * everything read is saved in the database and survives a restart.
 */
@Component
public class HistoryProgress {

    /** The status, the reason when it failed, and how many reports are read out of how many. */
    public record State(HistoryStatus status, String reason, int done, int total) {
    }

    private static final State IDLE = new State(HistoryStatus.IDLE, null, 0, 0);

    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public State of(Long portfolioId) {
        return states.getOrDefault(portfolioId, IDLE);
    }

    /** Starts reading {@code total} reports, unless this portfolio is being read already. True if it started. */
    public boolean begin(Long portfolioId, int total) {
        boolean[] started = {false};
        states.compute(portfolioId, (id, old) -> {
            if (old != null && old.status() == HistoryStatus.READING) return old;
            started[0] = true;
            return new State(HistoryStatus.READING, null, 0, total);
        });
        return started[0];
    }

    /** One more report read. */
    public void advance(Long portfolioId) {
        states.computeIfPresent(portfolioId,
                (id, state) -> new State(state.status(), state.reason(), Math.min(state.done() + 1, state.total()),
                        state.total()));
    }

    /** Reading stopped; {@code reason} says why. */
    public void fail(Long portfolioId, String reason) {
        states.computeIfPresent(portfolioId,
                (id, state) -> new State(HistoryStatus.FAILED, reason, state.done(), state.total()));
    }

    /** Reading is finished, so the portfolio is idle again. */
    public void finish(Long portfolioId) {
        states.remove(portfolioId);
    }
}
