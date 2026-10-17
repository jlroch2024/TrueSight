package com.truesight.news;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads Gemini's judgement of a batch of numbered headlines, and applies the guardrails, so only answers that can be
 * trusted are saved. See {@code docs/examples/gemini-news-assessment.json} for a real answer.
 *
 * <p>The answer's text is JSON: an object whose {@code assessments} is a list, as {@link NewsAssessor#NEWS_SCHEMA}
 * asks for, or the list on its own, as in the saved answer. Each item answers one headline by its {@code index}.
 * <ul>
 *   <li>An answer that is not JSON in either shape throws {@link UnreadableAnswerException}, so the caller can ask
 *       once more.</li>
 *   <li>An item whose index is missing, not one of the headlines sent, or already answered, is ignored. A headline
 *       with no item stays unassessed, and is sent again by the next news check.</li>
 *   <li>An item with an unknown confidence, no reason, or (for an event) an unknown event type or severity, is kept
 *       as noise with the reason {@link #UNUSABLE}. Its confidence is LOW, because the database needs one.</li>
 * </ul>
 */
public class NewsAssessmentReader {

    /** The reason saved when the AI's judgement of a headline breaks the rules. */
    public static final String UNUSABLE = "The AI's answer could not be used.";

    /** The longest summary or reason the database keeps. */
    private static final int MAX_TEXT = 500;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    /** One headline's judgement, ready to save. eventType, severity and summary are null for noise. */
    public record Judgement(boolean event, EventType eventType, Severity severity, Confidence confidence,
                            String summary, String reason) {

        static Judgement unusable() {
            return new Judgement(false, null, null, Confidence.LOW, null, UNUSABLE);
        }
    }

    /**
     * Reads Gemini's whole API response for a batch of {@code headlineCount} headlines, numbered from 1.
     *
     * @return each answered headline's judgement, by its number
     * @throws UnreadableAnswerException if the answer is not the JSON that was asked for
     */
    public Map<Integer, Judgement> read(String response, int headlineCount) {
        JsonNode answer;
        try {
            answer = mapper.readTree(answerText(response));
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        JsonNode items = answer != null && answer.isObject() ? answer.path("assessments") : answer;
        if (items == null || !items.isArray()) {
            throw new UnreadableAnswerException();
        }

        Map<Integer, Judgement> judgements = new LinkedHashMap<>();
        for (JsonNode item : items) {
            JsonNode index = item.path("index");
            if (!index.isIntegralNumber()) continue;
            int number = index.asInt();
            if (number < 1 || number > headlineCount || judgements.containsKey(number)) continue;
            judgements.put(number, judge(item));
        }
        return judgements;
    }

    /** One headline's judgement, or {@link Judgement#unusable()} if it breaks a guardrail. */
    private static Judgement judge(JsonNode item) {
        JsonNode isEvent = item.path("isEvent");
        Confidence confidence = valueOf(Confidence.class, text(item, "confidence"));
        String reason = text(item, "reason");
        if (!isEvent.isBoolean() || confidence == null || reason.isEmpty()) {
            return Judgement.unusable();
        }
        if (!isEvent.booleanValue()) {
            return new Judgement(false, null, null, confidence, null, cut(reason));
        }
        EventType eventType = valueOf(EventType.class, text(item, "eventType"));
        Severity severity = valueOf(Severity.class, text(item, "severity"));
        if (eventType == null || severity == null) {
            return Judgement.unusable();
        }
        String summary = text(item, "summary");
        return new Judgement(true, eventType, severity, confidence, summary.isEmpty() ? null : cut(summary),
                cut(reason));
    }

    /** Joins the text of the answer's parts, skipping Gemini's "thought" parts. */
    private String answerText(String response) {
        if (response == null || response.isBlank()) {
            throw new UnreadableAnswerException();
        }
        JsonNode parts;
        try {
            parts = mapper.readTree(response).path("candidates").path(0).path("content").path("parts");
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        if (text.isEmpty()) {
            throw new UnreadableAnswerException();
        }
        return text.toString();
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isString() ? value.stringValue().trim() : "";
    }

    /** The enum constant named {@code value}, ignoring capitals, or null if there is none. */
    private static <E extends Enum<E>> E valueOf(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static String cut(String value) {
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }

    /** Gemini's answer was not the JSON that was asked for. */
    public static class UnreadableAnswerException extends RuntimeException {
        public UnreadableAnswerException() {
            super("The AI's answer was not the JSON that was asked for.");
        }
    }
}
