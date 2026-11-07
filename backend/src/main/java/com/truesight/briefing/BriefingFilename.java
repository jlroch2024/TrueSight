package com.truesight.briefing;

import java.text.Normalizer;
import java.time.LocalDate;

/**
 * The name a briefing is saved under: {@code <Portfolio>-Briefing-2026-11-06.pdf}. The portfolio's name is made safe
 * for a file and for a response header (plain ASCII), so a quote, a slash or a new line in it can never break either.
 */
final class BriefingFilename {

    private static final int LONGEST_NAME = 60;

    private BriefingFilename() {
    }

    static String of(String portfolioName, LocalDate date) {
        // Letters lose their accents and anything else outside A to Z and 0 to 9 goes, so the name is plain ASCII: a
        // header can carry nothing more, and a browser would otherwise save a garbled name.
        String plain = portfolioName == null ? "" : Normalizer.normalize(portfolioName, Normalizer.Form.NFD);
        String safe = plain.replaceAll("[^A-Za-z0-9]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        if (safe.length() > LONGEST_NAME) {
            safe = safe.substring(0, LONGEST_NAME).replaceAll("-+$", "");
        }
        if (safe.isEmpty()) {
            safe = "Portfolio";
        }
        return safe + "-Briefing-" + date + ".pdf";
    }
}
