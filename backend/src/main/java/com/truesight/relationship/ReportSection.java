package com.truesight.relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the text of an annual report that is sent to the AI: its business and risk sections, then every paragraph
 * elsewhere in the report that names suppliers or customers.
 *
 * <ul>
 *   <li>10-K: from "Item 1. Business" up to "Item 2. Properties", which includes Item 1A Risk Factors.</li>
 *   <li>20-F: from "Item 3. Key Information" up to "Item 5. Operating and Financial Review(s) (and Prospects)".</li>
 * </ul>
 *
 * <p>Headings match any kind of space (a non-breaking or thin space too), and "Item 3.A". A mention of a heading in a
 * sentence ("see Item 2. Properties for") is not a heading: see {@link #isHeading}. Each heading also appears in the
 * table of contents, so every start heading is paired with the next end heading after it, and the longest pair wins.
 * A stretch under {@value #MIN_SHARE} of the report (a contents page or a cross-reference index) is not the section,
 * and the whole report is used instead.
 *
 * <p>The text sent is, up to {@value #MAX_CHARACTERS} characters and in whole paragraphs: the section (over the limit,
 * its supplier or customer paragraphs, then its supply-chain ones); then supplier or customer paragraphs elsewhere;
 * then supply-chain paragraphs elsewhere. Everything kept stays in the report's order.
 */
public final class ReportSection {

    static final int MAX_CHARACTERS = 200_000;

    /** A stretch shorter than this share of the report is not its section. */
    static final double MIN_SHARE = 0.05;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    /** Between "Item 3" and "Key Information": an optional ".A", then a full stop, colon or dash, and any spacing. */
    private static final String AFTER_NUMBER = "(?:\\s*\\.\\s*[a-d]\\b)?\\s*[.:\\-–—]?\\s*";

    private static final Pattern TEN_K_START = heading("1", "business");
    private static final Pattern TEN_K_END = heading("2", "properties");
    private static final Pattern TWENTY_F_START = heading("3", "key\\s+information");
    private static final Pattern TWENTY_F_END = heading("5", "operating\\s+and\\s+financial\\s+reviews?(?:\\s+and\\s+prospects)?");

    /**
     * Just before a mention, on its own line: an all-lower-case word ("see"), or a comma, semicolon, colon, bracket or
     * quote. Case matters here, so "Table of Contents Item 2." is still a heading.
     */
    private static final Pattern MENTION_BEFORE =
            Pattern.compile("(?:(?<![A-Za-z])[a-z]+|[,;:(\"“‘'])[ \\t\\u00a0’\"“]*$");

    /** Just after a mention: a comma, semicolon, bracket or quote, or a lower-case word ("for"). */
    private static final Pattern MENTION_AFTER = Pattern.compile("^(?:[ \\t\\u00a0]*[,;)\"”’']|[ \\t\\u00a0]+[a-z])");

    /** How much text after a match is looked at to tell a mention from a heading. */
    private static final int LOOK_AFTER = 40;

    /** Words that name a supplier or customer outright. These paragraphs are kept first. */
    private static final Pattern RELATIONSHIP_WORDS = Pattern.compile(
            "supplier|sole[\\s-]source|single[\\s-]source|customer|foundr(y|ies)|contract\\s+manufacturer"
                    + "|purchased\\s+from",
            FLAGS);

    /** Other supply-chain words. These paragraphs are kept next, if there is room. */
    private static final Pattern SUPPLY_CHAIN_WORDS = Pattern.compile(
            "suppl(y|ies|ied)|vendor|manufactur|procure|purchas|component|subcontract|outsourc|assembl|distributor"
                    + "|logistic|raw\\s+material|wafer",
            FLAGS);

    private ReportSection() {
    }

    /** The text to send to the AI for a report of this form ("10-K" or "20-F"). */
    public static String forAi(String form, String reportText) {
        int[] stretch = stretch(form, reportText);
        if (stretch == null) {
            return trim(reportText, MAX_CHARACTERS);
        }
        String section = trim(reportText.substring(stretch[0], stretch[1]), MAX_CHARACTERS);

        // The paragraphs outside the section, with where each starts. A paragraph the section starts or ends inside
        // is part of the section's own text, so it is not added again.
        List<int[]> outside = new ArrayList<>();
        int at = 0;
        while (at <= reportText.length()) {
            int end = reportText.indexOf('\n', at);
            if (end < 0) end = reportText.length();
            if (end <= stretch[0] || at >= stretch[1]) {
                outside.add(new int[] {at, end});
            }
            at = end + 1;
        }
        boolean[] kept = new boolean[outside.size()];
        int used = keepOutside(reportText, outside, kept, RELATIONSHIP_WORDS, section.length());
        keepOutside(reportText, outside, kept, SUPPLY_CHAIN_WORDS, used);

        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        for (int i = 0; i < outside.size(); i++) {
            if (!kept[i]) continue;
            String paragraph = reportText.substring(outside.get(i)[0], outside.get(i)[1]);
            (outside.get(i)[0] < stretch[0] ? before : after).add(paragraph);
        }
        // The section ends with the line break before its end heading; joined to what follows, that is one break.
        if (!after.isEmpty() && section.endsWith("\n")) section = section.substring(0, section.length() - 1);
        List<String> parts = new ArrayList<>(before);
        if (!section.isEmpty()) parts.add(section);
        parts.addAll(after);
        return String.join("\n", parts);
    }

    /**
     * The paragraphs that name a company (any of {@code names}, as whole words, in any capitals) and also have a
     * supplier or customer word, in their order in the report: at most {@code maxPassages} of them, and only as many
     * whole paragraphs as fit in {@code maxCharacters}, counting a line break between each. Empty when none does, as in
     * a report that names the company only as a competitor.
     */
    public static List<String> passagesNaming(String text, Collection<String> names, int maxPassages, int maxCharacters) {
        List<Pattern> named = names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name.strip()) + "(?![\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
        List<String> passages = new ArrayList<>();
        int used = 0;
        for (String paragraph : text.split("\n")) {
            if (passages.size() == maxPassages) {
                break;
            }
            if (!RELATIONSHIP_WORDS.matcher(paragraph).find()
                    || named.stream().noneMatch(name -> name.matcher(paragraph).find())) {
                continue;
            }
            int cost = paragraph.length() + (passages.isEmpty() ? 0 : 1);
            if (used + cost <= maxCharacters) {
                passages.add(paragraph);
                used += cost;
            }
        }
        return passages;
    }

    /** Marks each outside paragraph matching {@code words} that still fits, and returns the characters now used. */
    private static int keepOutside(String text, List<int[]> paragraphs, boolean[] kept, Pattern words, int used) {
        for (int i = 0; i < paragraphs.size(); i++) {
            if (kept[i]) continue;
            String paragraph = text.substring(paragraphs.get(i)[0], paragraphs.get(i)[1]);
            if (paragraph.isBlank() || !words.matcher(paragraph).find()) continue;
            int cost = paragraph.length() + (used == 0 ? 0 : 1);
            if (used + cost <= MAX_CHARACTERS) {
                kept[i] = true;
                used += cost;
            }
        }
        return used;
    }

    /** The business and risk sections, or the whole report if they are not found. */
    static String section(String form, String text) {
        int[] stretch = stretch(form, text);
        return stretch == null ? text : text.substring(stretch[0], stretch[1]);
    }

    /** Where the section starts and ends, or null when the report has no usable section and is used whole. */
    static int[] stretch(String form, String text) {
        return switch (form) {
            case "10-K" -> longestBetween(text, TEN_K_START, TEN_K_END);
            case "20-F" -> longestBetween(text, TWENTY_F_START, TWENTY_F_END);
            default -> null;
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

    /**
     * From a start heading up to the next end heading after it, choosing the longest such stretch. Only headings
     * count, never mentions. Null when there is none, or the longest is under {@value #MIN_SHARE} of the text.
     */
    private static int[] longestBetween(String text, Pattern start, Pattern end) {
        int[] longest = null;
        Matcher starts = start.matcher(text);
        Matcher ends = end.matcher(text);
        int from = 0;
        while (starts.find(from)) {
            from = starts.end();
            if (!isHeading(starts, text)) continue;
            int searchFrom = starts.end();
            while (ends.find(searchFrom)) {
                if (isHeading(ends, text)) {
                    if (longest == null || ends.start() - starts.start() > longest[1] - longest[0]) {
                        longest = new int[] {starts.start(), ends.start()};
                    }
                    break;
                }
                searchFrom = ends.end();
            }
        }
        if (longest == null || longest[1] - longest[0] < MIN_SHARE * text.length()) return null;
        return longest;
    }

    /**
     * A match is a heading unless it reads as a mention in a sentence: on its own line, the word before it is all lower
     * case, or a comma, colon, bracket or quote comes just before it; or a comma, quote or lower-case word comes just
     * after it.
     */
    static boolean isHeading(Matcher match, String text) {
        int lineStart = text.lastIndexOf('\n', match.start() - 1) + 1;
        String before = text.substring(lineStart, match.start());
        String after = text.substring(match.end(), Math.min(text.length(), match.end() + LOOK_AFTER));
        return !MENTION_BEFORE.matcher(before).find() && !MENTION_AFTER.matcher(after).find();
    }

    private static Pattern heading(String number, String title) {
        return Pattern.compile("\\bitem\\s*" + number + AFTER_NUMBER + title + "\\b", FLAGS);
    }
}
