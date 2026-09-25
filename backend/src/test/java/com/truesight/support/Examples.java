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

    /** Gemini's real, unchanged answer for that 10-K: 7 suppliers, each with a quote. */
    public static String geminiNvidia() {
        return read("gemini-nvidia.json");
    }

    private static String read(String name) {
        try {
            return Files.readString(Path.of("..", "docs", "examples", name), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
