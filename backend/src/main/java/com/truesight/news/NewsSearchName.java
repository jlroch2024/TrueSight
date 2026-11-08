package com.truesight.news;

import java.util.regex.Pattern;

/** Turns a stored legal company name into the shorter name used for a news search. */
public final class NewsSearchName {

    /** A state or old-name mark the SEC keeps after a name, such as {@code /DE} or {@code /NEW/}. */
    private static final Pattern SLASH_MARK = Pattern.compile("\\s*/[A-Za-z]{2,4}/?$");

    /** A legal ending, or a final Holding or Holdings, which newspapers do not print. {@code Group} stays. */
    private static final Pattern LEGAL_ENDING = Pattern.compile(
            "(?i)\\s+(?:inc(?:orporated)?|corp(?:oration)?|ltd|limited|co(?:mpany)?|plc|n\\.v\\.|s\\.a\\.|nv|sa|ag|se|llc"
                    + "|holdings?)\\.?$");

    private NewsSearchName() {
    }

    public static String of(String name) {
        String result = name == null ? "" : name.trim();
        int comma = result.indexOf(',');
        if (comma >= 0) result = result.substring(0, comma).trim();
        result = SLASH_MARK.matcher(result).replaceFirst("").trim();
        String previous;
        do {
            previous = result;
            result = LEGAL_ENDING.matcher(result).replaceFirst("").trim();
        } while (!result.equals(previous));
        return result;
    }
}
