package com.truesight.briefing;

import java.time.LocalDate;

/**
 * The name a briefing is saved under: {@code <Portfolio>-Briefing-2026-11-06.pdf}. The portfolio's name is made safe
 * for a file and for a response header, so a quote, a slash or a new line in it can never break either.
 */
final class BriefingFilename {

    private static final int LONGEST_NAME = 60;

    private BriefingFilename() {
    }

    static String of(String portfolioName, LocalDate date) {
        String safe = portfolioName == null ? "" : portfolioName.trim().replaceAll("[^\\p{L}\\p{N}]+", "-");
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
