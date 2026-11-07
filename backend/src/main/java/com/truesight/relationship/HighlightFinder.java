package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Asks the AI which words matter in each quote: the companies, what is supplied, how much one relies on the other, and
 * the figures. Nothing about what to highlight is written into the code.
 *
 * <p>A call is small: up to {@link #BATCH_SIZE} quotes, numbered from 1, never the report. It is recorded in the AI Audit
 * Trail with the purpose {@code HIGHLIGHT}, each quote replaced by {@code [quote of relationship <id>]}.
 *
 * <p>The AI never invents a highlight. Each phrase must be in its quote as a run of whole words, in order, with every word
 * present, ignoring capitals, spacing and punctuation as {@link QuoteChecker} does. One that is not is dropped. What is
 * kept is only where the phrase starts and ends in the evidence, so only the report's own words are ever marked.
 */
@Component
public class HighlightFinder {

    private static final Logger log = LoggerFactory.getLogger(HighlightFinder.class);

    /** The most quotes sent to Gemini at once. */
    static final int BATCH_SIZE = 25;

    /** The most phrases kept from one quote. */
    static final int MAX_PER_QUOTE = 8;

    static final String PROMPT = """
            Each numbered quote below is from a company's annual report, and states a supplier or customer relationship. \
            Pick out the words in each quote that someone checking the claim at a glance would want to see.

            Each phrase is one of these kinds:
            - COMPANY: a company the quote names, under any name it uses, such as "TSMC"
            - SUPPLIED: what is supplied
            - RELIANCE: how much one company relies on the other, such as "sole source" or "substantially all"
            - FIGURE: an amount, a share or a date

            Answer for every quote:
            - index: the quote's number
            - highlights: at most %d phrases, each with:
              - text: the phrase, copied exactly from that quote, word for word
              - kind: COMPANY, SUPPLIED, RELIANCE or FIGURE

            Never reword a phrase, or join words from different places. If a phrase matters each time it appears, list it \
            each time. A quote with nothing to pick out gets an empty list. Answer with JSON only.

            QUOTES:
            """.formatted(MAX_PER_QUOTE);

    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final AiCallLog calls;

    public HighlightFinder(GeminiClient gemini, GeminiAnswer answers, AiCallLog calls) {
        this.gemini = gemini;
        this.answers = answers;
        this.calls = calls;
    }

    /**
     * Asks Gemini once about these relationships, at most {@link #BATCH_SIZE}, and returns the checked highlights of every
     * one, by relationship id: a relationship with nothing found has an empty list, so its quote is not read again. If the
     * answer cannot be read, the call is marked so and the answer is empty, so the next pass reads them all again.
     *
     * @throws GeminiClient.GeminiException if Gemini fails
     */
    public Map<Long, List<RelationshipHighlight>> find(List<Relationship> batch) {
        AiAnswer answer = gemini.generateHighlights(new AiRequest(AiPurpose.HIGHLIGHT,
                prompt(batch, relationship -> oneLine(relationship.getEvidence())), null,
                prompt(batch, relationship -> "[quote of relationship %d]".formatted(relationship.getId()))));
        List<GeminiAnswer.HighlightFound> read;
        try {
            read = answers.readHighlights(answer.text());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            log.warn("The AI's highlights for {} quote(s) could not be read; the next pass reads them again.", batch.size());
            calls.unreadable(answer.callId());
            return Map.of();
        }

        Map<Long, List<RelationshipHighlight>> found = new HashMap<>();
        int kept = 0;
        for (int i = 0; i < batch.size(); i++) {
            Relationship relationship = batch.get(i);
            int index = i + 1;
            List<GeminiAnswer.HighlightFound> phrases = read.stream()
                    .filter(phrase -> phrase.index() == index).limit(MAX_PER_QUOTE).toList();
            List<RelationshipHighlight> highlights = check(relationship.getId(), relationship.getEvidence(), phrases);
            found.put(relationship.getId(), highlights);
            kept += highlights.size();
        }
        calls.finish(answer.callId(), kept, read.size() - kept);
        return found;
    }

    /** The prompt, with each quote numbered from 1 and written as {@code quote} gives it. */
    private static String prompt(List<Relationship> batch, Function<Relationship, String> quote) {
        StringBuilder text = new StringBuilder(PROMPT);
        for (int i = 0; i < batch.size(); i++) {
            text.append(i + 1).append(". ").append(quote.apply(batch.get(i))).append('\n');
        }
        return text.toString();
    }

    /**
     * The phrases that are in the evidence, in the order they appear there. Longest first, each takes the first place it
     * runs through the evidence that no longer phrase has taken, so where two overlap the longer stays (of two as long,
     * the one the AI gave first), and a phrase given twice takes its next place. One with no such place is dropped.
     */
    static List<RelationshipHighlight> check(Long relationshipId, String evidence,
                                             List<GeminiAnswer.HighlightFound> phrases) {
        List<QuoteChecker.Word> words = QuoteChecker.split(evidence);
        List<GeminiAnswer.HighlightFound> longestFirst = phrases.stream()
                .filter(phrase -> phrase.kind() != null)
                .sorted(Comparator.comparingInt((GeminiAnswer.HighlightFound phrase) -> phrase.text().length()).reversed())
                .toList();
        List<RelationshipHighlight> kept = new ArrayList<>();
        for (GeminiAnswer.HighlightFound phrase : longestFirst) {
            Range range = locate(phrase.text(), evidence, words, kept);
            if (range != null) {
                kept.add(new RelationshipHighlight(relationshipId, phrase.kind(), range.start(), range.end()));
            }
        }
        kept.sort(Comparator.comparingInt(RelationshipHighlight::getStartAt));
        return kept;
    }

    /** The first place the phrase's words run in order through the evidence, clear of {@code kept}. Null if nowhere. */
    private static Range locate(String phrase, String evidence, List<QuoteChecker.Word> words,
                                List<RelationshipHighlight> kept) {
        String text = phrase.strip();
        List<QuoteChecker.Word> wanted = QuoteChecker.split(text);
        if (wanted.isEmpty()) {
            return null;
        }
        for (int first = 0; first + wanted.size() <= words.size(); first++) {
            if (!runsFrom(first, wanted, words)) {
                continue;
            }
            Range range = withOwnPunctuation(text, wanted, evidence, words.get(first).start(),
                    words.get(first + wanted.size() - 1).end());
            if (kept.stream().noneMatch(range::overlaps)) {
                return range;
            }
        }
        return null;
    }

    private static boolean runsFrom(int first, List<QuoteChecker.Word> wanted, List<QuoteChecker.Word> words) {
        for (int i = 0; i < wanted.size(); i++) {
            if (!wanted.get(i).text().equals(words.get(first + i).text())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Widens the range over punctuation at the phrase's ends where the evidence has it too, so "$1.5 billion" and "10%"
     * are marked whole.
     */
    private static Range withOwnPunctuation(String phrase, List<QuoteChecker.Word> wanted, String evidence, int start,
                                            int end) {
        int before = wanted.get(0).start();
        while (before > 0 && start > 0 && phrase.charAt(before - 1) == evidence.charAt(start - 1)) {
            before--;
            start--;
        }
        int after = wanted.get(wanted.size() - 1).end();
        while (after < phrase.length() && end < evidence.length() && phrase.charAt(after) == evidence.charAt(end)) {
            after++;
            end++;
        }
        return new Range(start, end);
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    /** Where a phrase is in the evidence: {@code start} inclusive, {@code end} exclusive. */
    private record Range(int start, int end) {

        boolean overlaps(RelationshipHighlight other) {
            return start < other.getEndAt() && other.getStartAt() < end;
        }
    }
}
