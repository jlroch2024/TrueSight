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
