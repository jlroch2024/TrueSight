package com.truesight.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the real sample data in {@code docs/examples/}, whose README says where each file came from. Tests run in
 * {@code backend/}, so the folder is one level up.
 */
public final class Examples {

    private Examples() {
    }

    /** NVIDIA's latest 10-K as plain text, one paragraph per line. */
    public static String nvidia10K() {
        return read("nvidia-10k.txt");
    }

    /**
     * The SEC's submissions response for NVIDIA (CIK 0001045810), unchanged: the list of its filings, from which a
     * company's annual reports are found.
     */
    public static String secSubmissionsNvidia() {
        return read("sec-submissions-nvidia.json");
    }

    /** NVIDIA's 10-K filed in 2025, the one before {@link #nvidia10K()}, as plain text, one paragraph per line. */
    public static String nvidia10KOlder() {
        return read("nvidia-10k-older.txt");
    }

    /** Gemini's real, unchanged answer to the relationship prompt for {@link #nvidia10KOlder()}. */
    public static String geminiNvidiaOlder() {
        return read("gemini-nvidia-older.json");
    }

    /** NVIDIA's 10-K filed in 2024, the one before {@link #nvidia10KOlder()}, as plain text, one paragraph per line. */
    public static String nvidia10KOldest() {
        return read("nvidia-10k-oldest.txt");
    }

    /** Gemini's real, unchanged answer to the relationship prompt for {@link #nvidia10KOldest()}. */
    public static String geminiNvidiaOldest() {
        return read("gemini-nvidia-oldest.json");
    }

    /** AMD's latest 10-K as plain text, one paragraph per line. It names a customer, "OpenAI OpCo, LLC". */
    public static String amd10K() {
        return read("amd-10k.txt");
    }

    /**
     * Gemini's real, unchanged answer to the relationship prompt that also asks for each company's common name and
     * ticker, for {@link #amd10K()}.
     */
    public static String geminiAmd() {
        return read("gemini-amd.json");
    }

    /**
     * Gemini's real, unchanged answer to the common-names pass's prompt, for the names of the companies found in
     * {@link #geminiNvidia()} and {@link #geminiAmd()} as the pass would have found them stored.
     */
    public static String geminiCommonNames() {
        return read("gemini-common-names.json");
    }

    /** 17 relationships in the shape {@code GET /api/portfolios/{id}/relationships} returns. */
    public static String relationships() {
        return read("relationships.json");
    }

    /** Gemini's real, unchanged answer for that 10-K: 7 suppliers, each with a quote. */
    public static String geminiNvidia() {
        return read("gemini-nvidia.json");
    }

    /** Gemini's real answer to the criticality-enabled relationship prompt for NVIDIA's saved 10-K. */
    public static String geminiNvidiaCriticality() {
        return read("gemini-nvidia-criticality.json");
    }

    /**
     * Gemini's real answer when the second check was run by hand on the 7 relationships in {@link #geminiNvidia()}:
     * one verdict, Agrees or Doubts with a reason, for each numbered claim.
     */
    public static String geminiNvidiaSecondCheck() {
        return read("gemini-nvidia-second-check.json");
    }

    /** Gemini's real, unchanged answer to the highlights prompt, for {@link #highlightQuotes()}. */
    public static String geminiHighlights() {
        return read("gemini-highlights.json");
    }

    /** The 5 sentences of {@link #nvidia10K()} and {@link #amd10K()} sent for {@link #geminiHighlights()}, in order. */
    public static List<String> highlightQuotes() {
        return read("gemini-highlights-quotes.txt").lines().toList();
    }

    /** A real Google News RSS response for the TSMC search used by Sprint 2. */
    public static String googleNewsTsmc() {
        return read("google-news-tsmc.xml");
    }

    /** Gemini's real assessment of the first 20 headlines in {@link #googleNewsTsmc()}. */
    public static String geminiNewsAssessment() {
        return read("gemini-news-assessment.json");
    }

    /** Gemini's real recommendation response supplied during TS-183 implementation. */
    public static String geminiRecommendation() {
        return read("gemini-recommendation.json");
    }

    /** The sample portfolio: ten holdings, each weighted 10, as {@code ticker,weight} lines. */
    public static String holdingsCsv() {
        return read("holdings.csv");
    }

    /** Gemini's real answer to a question the sample portfolio's data can answer (see the README). */
    public static String geminiAskAnswerable() {
        return read("gemini-ask-answerable.json");
    }

    /** Gemini's real answer to a question the sample portfolio's data cannot answer. */
    public static String geminiAskUnknown() {
        return read("gemini-ask-unknown.json");
    }

    /** Gemini's real answer to a question asking what to buy. */
    public static String geminiAskAdvice() {
        return read("gemini-ask-advice.json");
    }

    /** The team's hand labels, event or noise, for the 20 headlines in {@link #geminiNewsAssessment()}. */
    public static String newsLabels() {
        return read("news-labels.csv");
    }

    /**
     * Clorox's real SEC submissions list (CIK 0000021076), trimmed to its {@code filings.recent} arrays: its 8-Ks
     * include ones reporting Item 8.01 and ones reporting none of the nine items a news check reads.
     */
    public static String secSubmissionsClorox() {
        return read("sec-submissions-clorox.json");
    }

    /** Clorox's real 8-K of 14 August 2023 (Item 8.01): it found unauthorized activity on its IT systems. */
    public static String sec8kClorox() {
        return read("sec-8k-clorox-2023-08-14.htm");
    }

    /**
     * Clorox's real 8-K of 22 January 2026 (Items 7.01 and 8.01): it agreed to buy GOJO. Its Item 7.01 mentions
     * "Item 8.01 below" before Item 8.01's own heading.
     */
    public static String sec8kCloroxGojo() {
        return read("sec-8k-clorox-2026-01-22.htm");
    }

    /** AITX's real 8-K of 30 October 2026 (Item 8.01), whose item only points to a press release, Exhibit 99.1. */
    public static String sec8kAitx() {
        return read("sec-8k-aitx-2026-10-30.htm");
    }

    /** Amgen's real 8-K of 31 July 2026 (Item 1.05, Material Cybersecurity Incidents): data exfiltrated from cloud environments. */
    public static String sec8kAmgen() {
        return read("sec-8k-amgen-2026-07-31.htm");
    }

    /**
     * Gemini's real, unchanged answer to Filter Out Noise's prompt for the headlines built from {@link #sec8kClorox()}
     * (1, an event), {@link #sec8kCloroxGojo()} (2) and {@link #sec8kAitx()} (3).
     */
    public static String gemini8kAssessment() {
        return read("gemini-8k-assessment.json");
    }

    /**
     * Cirrus Logic's tagged figures (XBRL) from its 10-K for the year to 28 March 2026, trimmed to the
     * concentration facts and what they use: Apple's revenue share for three years, Foxconn's and Luxshare's
     * receivables, and the ten largest customers' revenue share.
     */
    public static String cirrusFigures() {
        return read("crus-20260328_htm.xml");
    }

    /** The label file that goes with {@link #cirrusFigures()}. */
    public static String cirrusLabels() {
        return read("crus-20260328_lab.xml");
    }

    /** NVIDIA's tagged figures from its 10-K filed 2026-02-25: customer receivables shares, its customers unnamed. */
    public static String nvidiaFigures() {
        return read("nvda-20260125_htm.xml");
    }

    /** The label file that goes with {@link #nvidiaFigures()}. */
    public static String nvidiaLabels() {
        return read("nvda-20260125_lab.xml");
    }

    /** The unchanged folder listing of Cirrus Logic's filing, accession 0000772406-26-000018. */
    public static String secIndexCirrus() {
        return read("sec-index-cirrus-0000772406-26-000018.json");
    }

    /** docs/examples/README.md, which states how often Gemini agreed with {@link #newsLabels()}. */
    public static String readme() {
        return read("README.md");
    }

    /** Apple's 10-K filed 2026-11-03, through ReportTextExtractor: its headings have non-breaking spaces. */
    public static String apple10K() {
        return read("apple-10k.txt");
    }

    /** Nike's 10-K filed 2026-07-15, through ReportTextExtractor: sentences in Item 1 mention "Item 2. Properties". */
    public static String nike10K() {
        return read("nike-10k.txt");
    }

    /** Intel's 10-K filed 2026-01-23, through ReportTextExtractor: its Item headings are only in a cross-reference index. */
    public static String intel10K() {
        return read("intel-10k.txt");
    }

    /** 29 real annual reports' lengths, item lines and chosen sections, from make_report_section_filings.py. */
    public static String reportSectionFilings() {
        return read("report-section-filings.json");
    }

    private static String read(String name) {
        try {
            return Files.readString(Path.of("..", "docs", "examples", name), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
