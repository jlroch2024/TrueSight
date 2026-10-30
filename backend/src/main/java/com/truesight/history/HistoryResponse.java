package com.truesight.history;

import com.truesight.relationship.RelationshipType;

import java.time.LocalDate;
import java.util.List;

/**
 * Where reading a portfolio's earlier annual reports has got, and how its suppliers and customers changed year by year.
 *
 * <p>{@code progress} is how many earlier reports have been read out of how many are being read, and is null when
 * nothing is being read. {@code unread} is how many earlier reports are still to read. {@code years} runs from the
 * year of the earliest report read to the year of the latest; "Today" is the current graph, shown by the website.
 */
public record HistoryResponse(HistoryStatus status, String reason, Progress progress, int unread, List<Year> years) {

    public record Progress(int done, int total) {
    }

    /**
     * What the portfolio's holdings' reports named as of the end of one year, and what they named the year before and
     * no longer do ({@code gone}, as keys).
     */
    public record Year(int year, List<Entry> relationships, List<String> gone) {
    }

    /**
     * One relationship in one year. {@code key} is the same in every year (company, counterparty and type), so a
     * relationship can be followed from year to year. {@code reportUrl} and {@code filingDate} are the report it came
     * from, and {@code change} says whether the year before also named it.
     */
    public record Entry(String key, Long companyId, Long counterpartyId, RelationshipType type, String provides,
                        String evidence, String reportUrl, LocalDate filingDate, Change change) {
    }

    /** Named the year before too ({@code KEPT}), or not ({@code NEW}). */
    public enum Change {
        NEW,
        KEPT
    }
}
