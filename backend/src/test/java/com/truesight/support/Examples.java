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
