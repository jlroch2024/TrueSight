package com.truesight.relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the part of an annual report that is sent to the AI: the business and risk sections, not the whole report.
 *
 * <ul>
 *   <li>10-K: from "Item 1. Business" up to "Item 2. Properties", which includes Item 1A Risk Factors.</li>
 *   <li>20-F: from "Item 3. Key Information" up to "Item 5. Operating and Financial Review".</li>
 * </ul>
 *
 * <p>Each heading also appears in the table of contents, so every start heading is paired with the next end heading
 * after it, and the longest pair wins. If the headings are not found, the whole report is used. If the result is over
 * {@value #MAX_CHARACTERS} characters, only whole paragraphs are kept: first those with relationship words, then other
 * supply-chain paragraphs, in their original order.
 */
public final class ReportSection {

    static final int MAX_CHARACTERS = 200_000;

    /** Between "Item 1" and "Business": a full stop, colon or dash, and any spacing, including a line break. */
    private static final String AFTER_NUMBER = "\\s*[.:\\-–—]?\\s*";

    private static final Pattern TEN_K_START = heading("1", "business");
    private static final Pattern TEN_K_END = heading("2", "properties");
    private static final Pattern TWENTY_F_START = heading("3", "key\\s+information");
    private static final Pattern TWENTY_F_END = heading("5", "operating\\s+and\\s+financial\\s+review");

    /** Words that name a supplier or customer outright. These paragraphs are kept first. */
    private static final Pattern RELATIONSHIP_WORDS = Pattern.compile(
            "supplier|sole[\\s-]source|single[\\s-]source|customer|foundr(y|ies)|contract\\s+manufacturer"
                    + "|purchased\\s+from",
            Pattern.CASE_INSENSITIVE);

    /** Other supply-chain words. These paragraphs are kept next, if there is room. */
    private static final Pattern SUPPLY_CHAIN_WORDS = Pattern.compile(
            "suppl(y|ies|ied)|vendor|manufactur|procure|purchas|component|subcontract|outsourc|assembl|distributor"
                    + "|logistic|raw\\s+material|wafer",
            Pattern.CASE_INSENSITIVE);

    private ReportSection() {
    }

    /** The text to send to the AI for a report of this form ("10-K" or "20-F"). */
    public static String forAi(String form, String reportText) {
        return trim(section(form, reportText), MAX_CHARACTERS);
    }

    /** The business and risk sections, or the whole report if their headings are not found. */
    static String section(String form, String text) {
        return switch (form) {
            case "10-K" -> longestBetween(text, TEN_K_START, TEN_K_END);
            case "20-F" -> longestBetween(text, TWENTY_F_START, TWENTY_F_END);
            default -> text;
        };
    }

    /**
     * Keeps whole paragraphs (lines) until {@code limit} characters: relationship paragraphs first, then other
     * supply-chain paragraphs. The kept paragraphs stay in their original order.
     */
    static String trim(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        String[] paragraphs = text.split("\n");
        boolean[] kept = new boolean[paragraphs.length];
        int used = keep(paragraphs, kept, RELATIONSHIP_WORDS, 0, limit);
        keep(paragraphs, kept, SUPPLY_CHAIN_WORDS, used, limit);

        List<String> result = new ArrayList<>();
        for (int i = 0; i < paragraphs.length; i++) {
            if (kept[i]) {
                result.add(paragraphs[i]);
            }
        }
        return String.join("\n", result);
    }

    /** Marks each paragraph matching {@code words} that still fits, and returns the characters now used. */
    private static int keep(String[] paragraphs, boolean[] kept, Pattern words, int used, int limit) {
        for (int i = 0; i < paragraphs.length; i++) {
            if (kept[i] || !words.matcher(paragraphs[i]).find()) {
                continue;
            }
            // Each kept paragraph after the first also needs its line break.
            int cost = paragraphs[i].length() + (used == 0 ? 0 : 1);
            if (used + cost <= limit) {
                kept[i] = true;
                used += cost;
            }
        }
        return used;
    }

    /** From a start heading up to the next end heading after it, choosing the longest such stretch. */
    private static String longestBetween(String text, Pattern start, Pattern end) {
        String longest = null;
        Matcher starts = start.matcher(text);
        Matcher ends = end.matcher(text);
        while (starts.find()) {
            if (ends.find(starts.end())) {
                String candidate = text.substring(starts.start(), ends.start());
                if (longest == null || candidate.length() > longest.length()) {
                    longest = candidate;
                }
            }
        }
        return longest == null ? text : longest;
    }

    private static Pattern heading(String number, String title) {
        return Pattern.compile("\\bitem\\s*" + number + AFTER_NUMBER + title + "\\b", Pattern.CASE_INSENSITIVE);
    }
}
