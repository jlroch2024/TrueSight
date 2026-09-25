package com.truesight.relationship;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads Gemini's answer: the text in {@code candidates[0].content.parts}, which should be a JSON list of
 * {@code { company, alsoKnownAs, relationship, provides, quote }}. See {@code docs/examples/gemini-nvidia.json}.
 *
 * <p>An answer that is not a JSON list throws {@link UnreadableAnswerException}, so the caller can ask once more. An
 * item in the list with no company or quote, or a relationship other than SUPPLIER or CUSTOMER, is left out.
 */
@Component
public class GeminiAnswer {

    private final ObjectMapper mapper;

    public GeminiAnswer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** One relationship as the AI gave it. Its quote is not checked yet. */
    public record Found(String company, String alsoKnownAs, RelationshipType relationship, String provides,
                        String quote) {
    }

    public List<Found> read(String response) {
        JsonNode list;
        try {
            list = mapper.readTree(answerText(response));
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        if (list == null || !list.isArray()) {
            throw new UnreadableAnswerException();
        }
        List<Found> found = new ArrayList<>();
        for (JsonNode item : list) {
            String company = text(item, "company");
            String quote = text(item, "quote");
            RelationshipType type = type(text(item, "relationship"));
            if (company.isEmpty() || quote.isEmpty() || type == null) {
                continue;
            }
            found.add(new Found(company, text(item, "alsoKnownAs"), type, text(item, "provides"), quote));
        }
        return found;
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

    private static RelationshipType type(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SUPPLIER" -> RelationshipType.SUPPLIER;
            case "CUSTOMER" -> RelationshipType.CUSTOMER;
            default -> null;
        };
    }

    /** Gemini's answer was not the JSON list that was asked for. */
    public static class UnreadableAnswerException extends RuntimeException {
        public UnreadableAnswerException() {
            super("The AI's answer was not the JSON list that was asked for.");
        }
    }
}
