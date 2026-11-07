package com.truesight.ask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed sentences of Ask TrueSight and the checks that keep an answer inside its rules, whatever Gemini says.
 * The prompt asks for the same things; these are the backstop.
 */
final class AskGuards {

    /** Said when the portfolio's data does not hold the answer. Shown with no sources. */
    static final String UNKNOWN = "TrueSight's data does not answer that.";

    /**
     * Said to a question about what to trade. Shown with no sources. It avoids the words the advice check looks for.
     * The story gives no wording; this one is ours.
     */
    static final String NO_ADVICE = "TrueSight offers research support, not investment advice, so it cannot say what to trade.";

    /** Added when an answer rests on a relationship no analyst has reviewed and does not say so itself. */
    static final String UNREVIEWED_NOTE = "Part of this answer rests on unreviewed relationships that no analyst has checked yet.";

    // Advice is addressed to the user ("you should buy", "consider selling"), is an order ("Hold your shares."), or names
    // a rating or a price target. "Dell and HP buy GPUs from NVIDIA" describes a relationship and is not advice.
    private static final Pattern ADVICE = Pattern.compile(
            "\\b(you|your|investors?)\\b\\W+(?:\\w+\\W+){0,3}?(buy|sell|hold)\\b"
                    + "|\\b(consider|recommend|recommends|suggest|suggests|advise|advises|worth)\\s+(?:\\w+\\s+){0,2}?"
                    + "(buy|sell|hold|buying|selling|holding)\\b"
                    + "|\\b(buy|sell|hold)\\s+(rating|recommendation|signal)\\b"
                    + "|\\b(upgrade|upgrades|downgrade|downgrades)\\s+to\\s+(buy|sell|hold)\\b"
                    + "|\\bprice\\s+targets?\\b|\\b(overweight|underweight)\\b"
                    + "|(?:^|[.!?;:\"“]\\s*)(buy|sell|hold)\\b(?!-)"
                    + "|(?:,|\\b(?:so|then|just|now))\\s*(buy|sell|hold)\\s+(?:more|less|it|them|your|some|all)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern QUOTED = Pattern.compile("[\"“]([^\"”]+)[\"”]");

    private AskGuards() {
    }

    /** An id as Gemini may write it ("[r12]", " R12 ") made into the one the records use ("R12"). */
    static String idKey(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\[\\]\\s]", "").toUpperCase();
    }

    /** Whether the answer is one of the two fixed sentences, which are allowed to have no sources. */
    static boolean isRefusal(String answer) {
        String text = normalise(answer);
        return text.equals(UNKNOWN) || text.equals(NO_ADVICE);
    }

    /** The fixed sentence the answer is, or null if it is neither. */
    static String refusalOf(String answer) {
        String text = normalise(answer);
        if (text.equals(UNKNOWN)) {
            return UNKNOWN;
        }
        return text.equals(NO_ADVICE) ? NO_ADVICE : null;
    }

    /**
     * Whether the answer reads as advice to trade: it tells the user to buy, sell or hold, or names a rating or a price
     * target. The words alone are not enough, as a supply-chain answer says that one company buys from another. Words
     * inside a quotation copied from the records are not the answer's own, so a report's "we sell wafers" does not
     * count.
     */
    static boolean isAdvice(String answer, String records) {
        Matcher quote = QUOTED.matcher(answer);
        StringBuilder own = new StringBuilder();
        int from = 0;
        while (quote.find()) {
            if (records.contains(quote.group(1))) {
                own.append(answer, from, quote.start());
                from = quote.end();
            }
        }
        own.append(answer.substring(from));
        return ADVICE.matcher(own.toString()).find();
    }

    // "No unreviewed relationships" and "none of them is unreviewed" say the opposite of what the note is for.
    private static final Pattern NEGATED_UNREVIEWED =
            Pattern.compile("\\b(no|not|none|without|neither|nor|isn't|aren't)\\b[^.;!?]{0,40}unreviewed");

    /** Whether the answer tells the reader that part of it rests on unreviewed relationships. */
    static boolean mentionsUnreviewed(String answer) {
        String text = answer.toLowerCase().replace('’', '\'');
        return text.contains("unreviewed") && !NEGATED_UNREVIEWED.matcher(text).find();
    }

    private static String normalise(String answer) {
        return answer == null ? "" : answer.replace('’', '\'').replaceAll("\\s+", " ").strip();
    }
}
