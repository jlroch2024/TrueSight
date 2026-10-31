package com.truesight.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /**
     * Gemini's real, unchanged answer to Filter Out Noise's prompt for the headlines built from {@link #sec8kClorox()}
     * (1, an event), {@link #sec8kCloroxGojo()} (2) and {@link #sec8kAitx()} (3).
     */
    public static String gemini8kAssessment() {
        return read("gemini-8k-assessment.json");
    }

    /** docs/examples/README.md, which states how often Gemini agreed with {@link #newsLabels()}. */
    public static String readme() {
        return read("README.md");
    }

    private static String read(String name) {
        try {
            return Files.readString(Path.of("..", "docs", "examples", name), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
