package com.truesight.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Saves the AI Audit Trail: one {@link AiCall} row per call to Gemini.
 *
 * <p>{@link #record} runs in its own transaction ({@code REQUIRES_NEW}), so the row stays even when the caller's work
 * is rolled back, which is exactly when a failed call matters most. It is saved without its report: the caller may
 * still be saving that report in its own, unfinished transaction, which a separate transaction cannot see.
 *
 * <p>The other methods join the caller's transaction instead. {@link #attachReport} can then see the report, and none
 * of them waits on a row the caller is changing. If the caller's work is rolled back, the row stays, still naming the
 * report in its prompt's marker.
 */
@Component
public class AiCallLog {

    /** The longest error the {@code ai_calls} table accepts. */
    private static final int MAX_ERROR = 500;

    private final AiCallRepository calls;

    public AiCallLog(AiCallRepository calls) {
        this.calls = calls;
    }

    /** Records one call and returns its id. {@code error} is the reason shown to the user, never the API key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long record(AiRequest request, String model, String response, AiOutcome outcome, String error,
                       Instant startedAt, int durationMs) {
        String shortError = error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
        return calls.save(new AiCall(request.purpose(), model, request.recordedPrompt(), null, response, outcome,
                shortError, startedAt, durationMs)).getId();
    }

    /** Names the report a call's prompt quoted, once the caller's transaction can see it. */
    @Transactional
    public void attachReport(Long callId, Long reportId) {
        if (callId == null || reportId == null) return;
        calls.findById(callId).ifPresent(call -> call.setReportId(reportId));
    }

    /** Marks a call whose answer arrived but could not be read. Does nothing for an unknown id. */
    @Transactional
    public void unreadable(Long callId) {
        if (callId == null) return;
        calls.findById(callId).ifPresent(call -> call.setOutcome(AiOutcome.UNREADABLE));
    }

    /** Records how many of the answer's quotes were kept and how many dropped. Does nothing for an unknown id. */
    @Transactional
    public void finish(Long callId, int kept, int dropped) {
        if (callId == null) return;
        calls.findById(callId).ifPresent(call -> {
            call.setKept(kept);
            call.setDropped(dropped);
        });
    }
}
