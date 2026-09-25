package com.truesight.portfolio;

/** Where the analysis has got for one holding. The website shows these as Waiting, Analysing, Done and so on. */
public enum HoldingStatus {
    /** Not analysed yet. Every new holding starts here. */
    WAITING,
    /** The analysis is working on it now. */
    ANALYSING,
    /** Finished. Its report and relationships are ready. */
    DONE,
    /** Something went wrong. The holding's status reason says what. */
    FAILED,
    /** The company has no 10-K or 20-F. A fact about the company, not an error. */
    NO_REPORT_FOUND
}
