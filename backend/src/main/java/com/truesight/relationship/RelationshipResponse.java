package com.truesight.relationship;

import com.truesight.report.ConcentrationMeasure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One relationship, as the website sees it, in the shape of {@code docs/examples/relationships.json}.
 *
 * <p>{@code company} is the company whose report it came from; {@code counterparty} is the other company.
 * {@code type} SUPPLIER means the counterparty supplies the company; CUSTOMER means it buys from it. {@code evidence}
 * is the report's own words. {@code provides} and a company's {@code ticker} may be null. A company's {@code name} is
 * as stored; {@code displayName} is how every page shows it (see {@link com.truesight.company.CompanyDisplayNames}).
 *
 * <p>{@code reviewStatus} is where an Analyst's review has got; {@code reviewedAt} and {@code reviewNote} are null
 * until a decision is made (and the note may stay empty for an approval).
 *
 * <p>{@code foundBy} is REPORT when it was found by reading the company's whole report, and NAME_SEARCH when a search
 * for the counterparty's name found it in a few passages of that report (see {@link NameSearcher}).
 *
 * <p>{@code confidence} is how confident TrueSight is about it, with its reasons (see {@link RelationshipConfidence}).
 *
 * <p>{@code highlights} are the words that matter in the evidence, in the order they appear (see
 * {@link HighlightFinder}). Empty when there are none.
 *
 * <p>{@code agreesWith} is the same relationship as the other company's own report states it (see {@link Agreement}), or
 * null when only this report names it.
 *
 * <p>{@code figure} is the latest revenue or purchases share this report tags for the pair, or null: never estimated.
 */
public record RelationshipResponse(
        Long id,
        CompanySummary company,
        CompanySummary counterparty,
        RelationshipType type,
        String provides,
        String evidence,
        RelationshipSource source,
        FoundBy foundBy,
        Criticality criticality,
        String criticalityEvidence,
        ReportSummary report,
        ReviewStatus reviewStatus,
        Instant reviewedAt,
        String reviewNote,
        RelationshipConfidence confidence,
        List<Highlight> highlights,
        AgreesWith agreesWith,
        Figure figure) {

    /** The other company's report saying the same thing: its relationship, its own sentence and that report. */
    public record AgreesWith(Long id, String evidence, ReportSummary report) {
    }

    /** A dependency figure from the report's own tags: {@code share} is from 0 to 1. */
    public record Figure(java.math.BigDecimal share, ConcentrationMeasure measure, java.time.LocalDate periodEnd) {
    }

    public record CompanySummary(Long id, String name, String displayName, String ticker) {
    }

    /** Words in the evidence: {@code start} is where they begin and {@code end} is just after they finish. */
    public record Highlight(HighlightKind kind, int start, int end) {
    }

    /** The report the evidence is in. {@code url} opens it on the SEC website. */
    public record ReportSummary(String form, LocalDate filingDate, String url) {
    }
}
