package com.truesight.relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that a quote from the AI really appears in the report, and finds the report's own words for it.
 *
 * <p>Capitals, spacing and punctuation are ignored: both texts are compared as lists of words. A quote passes if at
 * least 85% of its words appear in the same order in one passage of the report that is not much longer than the quote,
 * and every number and negative word ({@link #NEGATIVE_WORDS}) in the quote is among them, so "we do not buy" never
 * passes for "we do buy". The passage, as the report writes it, becomes the evidence.
 *
 * <p>Make one checker per report: the report is split into words once, then any number of quotes are checked.
 */
public final class QuoteChecker {

    /** At least this share of the quote's words must be found. */
    private static final double MIN_SHARE_FOUND = 0.85;

    /** The passage may be at most this much longer than the quote, in words. */
    private static final double MAX_PASSAGE_LENGTH = 1.25;

    static final Set<String> NEGATIVE_WORDS = Set.of("not", "no", "never", "none", "without", "cannot");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    /** Punctuation kept when it touches the passage's first or last word, e.g. an opening bracket or a full stop. */
    private static final String OPENING = "(\"'“‘[$";
    private static final String CLOSING = ".!?%)]\"'”’";

    private final String report;
    private final List<Word> words;

    public QuoteChecker(String report) {
        this.report = report;
        this.words = split(report);
    }

    /** The report's passage that matches the quote, or empty if the quote is not in the report. */
    public Optional<String> evidenceFor(String quote) {
        List<Word> quoteWords = split(quote);
        int n = quoteWords.size();
        if (n == 0) {
            return Optional.empty();
        }
        String[] q = quoteWords.stream().map(Word::text).toArray(String[]::new);
        boolean[] critical = new boolean[n];
        int criticalCount = 0;
        Map<String, Integer> quoteCounts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            critical[i] = isCritical(q[i]);
            if (critical[i]) {
                criticalCount++;
            }
            quoteCounts.merge(q[i], 1, Integer::sum);
        }
        int needed = (int) Math.ceil(MIN_SHARE_FOUND * n);
        int window = (int) Math.ceil(MAX_PASSAGE_LENGTH * n);

        Match best = null;
        // Slide a window of report words, counting how many of the quote's words it holds. Only a window holding
        // enough of them could pass, so only those are compared word by word.
        Map<String, Integer> windowCounts = new HashMap<>();
        int inCommon = 0;
        for (int end = 0; end < words.size(); end++) {
            inCommon += add(windowCounts, quoteCounts, words.get(end).text());
            int start = end - window + 1;
            if (start > 0) {
                inCommon -= remove(windowCounts, quoteCounts, words.get(start - 1).text());
            }
            if (start < 0 || inCommon < needed || !quoteCounts.containsKey(words.get(start).text())) {
                continue;
            }
            Match match = align(q, critical, start, Math.min(words.size(), start + window));
            if (match.criticalFound == criticalCount && match.found >= needed && match.betterThan(best)) {
                best = match;
            }
        }
        // The last few words of the report are shorter than a full window.
        for (int start = Math.max(0, words.size() - window + 1); start < words.size(); start++) {
            if (!quoteCounts.containsKey(words.get(start).text())) {
                continue;
            }
            Match match = align(q, critical, start, words.size());
            if (match.criticalFound == criticalCount && match.found >= needed && match.betterThan(best)) {
                best = match;
            }
        }
        return best == null ? Optional.empty() : Optional.of(passage(best.first, best.last));
    }

    /**
     * Lines the quote up against report words {@code from} (inclusive) to {@code to} (exclusive), keeping the order,
     * so that as many numbers and negative words as possible are found, then as many words as possible.
     */
    private Match align(String[] q, boolean[] critical, int from, int to) {
        int n = q.length;
        int m = to - from;
        int weight = n + 1; // one number or negative word outweighs every other word together
        int[][] score = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                int best = Math.max(score[i + 1][j], score[i][j + 1]);
                if (q[i].equals(words.get(from + j).text())) {
                    best = Math.max(best, score[i + 1][j + 1] + (critical[i] ? weight : 1));
                }
                score[i][j] = best;
            }
        }
        int found = 0;
        int criticalFound = 0;
        int first = -1;
        int last = -1;
        for (int i = 0, j = 0; i < n && j < m; ) {
            if (q[i].equals(words.get(from + j).text())
                    && score[i][j] == score[i + 1][j + 1] + (critical[i] ? weight : 1)) {
                found++;
                if (critical[i]) {
                    criticalFound++;
                }
                if (first < 0) {
                    first = from + j;
                }
                last = from + j;
                i++;
                j++;
            } else if (score[i + 1][j] >= score[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return new Match(found, criticalFound, first, last);
    }

    /** The report's own text from word {@code first} to word {@code last}, with the punctuation that touches it. */
    private String passage(int first, int last) {
        int start = words.get(first).start();
        int end = words.get(last).end();
        while (start > 0 && OPENING.indexOf(report.charAt(start - 1)) >= 0) {
            start--;
        }
        while (end < report.length() && CLOSING.indexOf(report.charAt(end)) >= 0) {
            end++;
        }
        return report.substring(start, end);
    }

    private static boolean isCritical(String word) {
        return NEGATIVE_WORDS.contains(word) || word.chars().anyMatch(Character::isDigit);
    }

    /** Adds a word to the window, returning 1 if it is one more of the quote's words. */
    private static int add(Map<String, Integer> windowCounts, Map<String, Integer> quoteCounts, String word) {
        Integer inQuote = quoteCounts.get(word);
        if (inQuote == null) {
            return 0;
        }
        int count = windowCounts.merge(word, 1, Integer::sum);
        return count <= inQuote ? 1 : 0;
    }

    /** Takes a word out of the window, returning 1 if it was one of the quote's words. */
    private static int remove(Map<String, Integer> windowCounts, Map<String, Integer> quoteCounts, String word) {
        Integer inQuote = quoteCounts.get(word);
        if (inQuote == null) {
            return 0;
        }
        int count = windowCounts.merge(word, -1, Integer::sum);
        return count < inQuote ? 1 : 0;
    }

    private static List<Word> split(String text) {
        List<Word> result = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            result.add(new Word(matcher.group().toLowerCase(Locale.ROOT), matcher.start(), matcher.end()));
        }
        return result;
    }

    /** One word, lower-cased, and where it is in the original text. */
    private record Word(String text, int start, int end) {
    }

    /** How well the quote lined up, and which report words it lined up with. */
    private record Match(int found, int criticalFound, int first, int last) {

        /**
         * More words found wins. With as many found, the shorter passage wins, so the evidence does not start with a
         * word borrowed from the sentence before.
         */
        boolean betterThan(Match other) {
            return other == null || found > other.found
                    || (found == other.found && last - first < other.last - other.first);
        }
    }
}
