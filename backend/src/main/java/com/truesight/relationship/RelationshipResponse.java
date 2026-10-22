package com.truesight.relationship;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One relationship, as the website sees it, in the shape of {@code docs/examples/relationships.json}.
 *
 * <p>{@code company} is the company whose report it came from; {@code counterparty} is the other company.
 * {@code type} SUPPLIER means the counterparty supplies the company; CUSTOMER means it buys from it. {@code evidence}
 * is the report's own words. {@code provides} and a company's {@code ticker} may be null.
 *
 * <p>{@code reviewStatus} is where an Analyst's review has got; {@code reviewedAt} and {@code reviewNote} are null
 * until a decision is made (and the note may stay empty for an approval).
 */
public record RelationshipResponse(
        Long id,
        CompanySummary company,
        CompanySummary counterparty,
        RelationshipType type,
        String provides,
        String evidence,
        Criticality criticality,
        String criticalityEvidence,
        ReportSummary report,
        ReviewStatus reviewStatus,
        Instant reviewedAt,
        String reviewNote) {

    public record CompanySummary(Long id, String name, String ticker) {
    }

    /** The report the evidence is in. {@code url} opens it on the SEC website. */
    public record ReportSummary(String form, LocalDate filingDate, String url) {
    }
}
