package com.truesight.company;

/**
 * Where reading a company's own annual report has got, for a company that is not read as a holding (a supplier, say).
 * A holding's own progress is {@code HoldingStatus}.
 */
public enum ReportStatus {
    /** Asked for, not started yet. */
    WAITING,
    /** The report is being read now. */
    ANALYSING,
    /** Finished. Its report and relationships are ready. */
    DONE,
    /** Something went wrong. The company's report status reason says what. */
    FAILED,
    /** The company has no 10-K or 20-F. A fact about the company, not an error. */
    NO_REPORT_FOUND
}
