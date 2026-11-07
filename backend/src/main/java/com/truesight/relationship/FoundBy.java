package com.truesight.relationship;

/**
 * How a relationship was found. REPORT: by reading the whole report. NAME_SEARCH: by reading the passages of another
 * company's report that name a holding. Only REPORT relationships count when deciding whether a report has been read.
 */
public enum FoundBy {
    REPORT,
    NAME_SEARCH
}
