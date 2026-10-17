package com.truesight.news;

import java.util.regex.Pattern;

/** Turns a stored legal company name into the shorter name used for a news search. */
public final class NewsSearchName {

    private static final Pattern LEGAL_ENDING = Pattern.compile(
            "(?i)\\s+(?:inc(?:orporated)?|corp(?:oration)?|ltd|limited|co(?:mpany)?|plc|n\\.v\\.|s\\.a\\.|ag|se|llc)\\.?$");

    private NewsSearchName() {
    }

    public static String of(String name) {
        String result = name == null ? "" : name.trim();
        int comma = result.indexOf(',');
        if (comma >= 0) result = result.substring(0, comma).trim();
        String previous;
        do {
            previous = result;
            result = LEGAL_ENDING.matcher(result).replaceFirst("").trim();
        } while (!result.equals(previous));
        return result;
    }
}
