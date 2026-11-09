package com.truesight.relationship;

import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The brake on what reading annual reports costs: at most {@code AI_DAILY_REPORT_LIMIT} reports (100 unless it is
 * set) are sent to Gemini in a day, counted across the whole app, whoever started them.
 *
 * <p>It counts the AI Audit Trail ({@code ai_calls}), so there is no table of its own and the count holds across
 * servers. What counts is {@code FIND_RELATIONSHIPS} (a holding's or a supplier's report) and {@code TIMELINE} (an
 * earlier report) calls that started since midnight UTC, however they ended: a failed call may have cost money.
 * News and second checks do not count, because they are small and stopping them would hide alerts. A report already
 * read is never sent again, so it never counts either.
 *
 * <p>A name search has a cap of its own ({@code AI_DAILY_NAME_SEARCH_LIMIT}, 500 unless it is set): one holding can
 * send up to 25 filings' passages, each about a twentieth of the cost of a report, so counting them against the reports
 * would let a ten-holding portfolio use up the day's reading and fail the next holding that needs its report read.
 *
 * <p>A name search has a cap of its own ({@code AI_DAILY_NAME_SEARCH_LIMIT}, 500 unless it is set): one holding can
 * send up to 25 filings' passages, each about a twentieth of the cost of a report, so counting them against the reports
 * would let a ten-holding portfolio use up the day's reading and fail the next holding that needs its report read.
 *
 * <p>It is a brake, not an invoice: two jobs asking at the same moment may both pass it, by a few reports.
 */
@Component
public class ReadingLimit {

    /** Why a report was not read, in the words shown as a holding's Failed reason. */
    public static final String REACHED_MESSAGE =
            "TrueSight has read as many new reports as it can today. Press Analyse again tomorrow.";

    /** The calls that read a report. */
    private static final List<AiPurpose> READS = List.of(AiPurpose.FIND_RELATIONSHIPS, AiPurpose.TIMELINE);

    /** The calls that read a filing's passages for a name search. */
    private static final List<AiPurpose> NAME_SEARCHES = List.of(AiPurpose.NAME_SEARCH);

    private final AiCallRepository calls;
    private final int limit;
    private final int nameSearchLimit;

    public ReadingLimit(AiCallRepository calls, @Value("${truesight.ai.daily-report-limit}") int limit,
                        @Value("${truesight.ai.daily-name-search-limit}") int nameSearchLimit) {
        this.calls = calls;
        this.limit = limit;
        this.nameSearchLimit = nameSearchLimit;
    }

    /** True once today's reading calls have reached the limit. */
    public boolean reached() {
        return callsToday(READS) >= limit;
    }

    /** True once today's name-search calls have reached their own limit. */
    public boolean nameSearchesReached() {
        return callsToday(NAME_SEARCHES) >= nameSearchLimit;
    }

    private long callsToday(List<AiPurpose> purposes) {
        Instant midnight = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return calls.countByPurposeInAndStartedAtGreaterThanEqual(purposes, midnight);
    }

    /**
     * Stops a name search from sending another filing's passages once its own limit is reached.
     *
     * @throws ReachedException so the search stops, and is not recorded, and a later Analyse carries on
     */
    public void requireRoomForNameSearch() {
        if (nameSearchesReached()) {
            throw new ReachedException();
        }
    }

    /**
     * Stops a report from being read once the limit is reached.
     *
     * @throws ReachedException with the reason to show, which the callers that mark a holding Failed already show
     */
    public void requireRoom() {
        if (reached()) {
            throw new ReachedException();
        }
    }

    /**
     * Today's limit is reached. It is a {@link GeminiClient.GeminiException}, so a holding's analysis shows its message
     * as the reason it Failed, exactly as for any other AI failure. A supplier's report, which is not a holding, is
     * told apart by this type and left waiting instead of Failed.
     */
    public static class ReachedException extends GeminiClient.GeminiException {
        public ReachedException() {
            super(REACHED_MESSAGE, null);
        }
    }
}
