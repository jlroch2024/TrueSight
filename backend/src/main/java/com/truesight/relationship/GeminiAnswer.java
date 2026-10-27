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
 * {@code { company, alsoKnownAs, commonName, ticker, relationship, provides, quote, criticality, criticalityQuote }}.
 * See {@code docs/examples/gemini-nvidia.json} and {@code docs/examples/gemini-amd.json}. Answers saved before
 * {@code commonName} and {@code ticker} were asked for have neither: both then read as empty.
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

    /**
     * One relationship as the AI gave it. Its quote is not checked yet. {@code company} is the name as written in the
     * report; {@code commonName} and {@code ticker} come from the AI's own knowledge, not the report, so they cannot
     * be quote-checked. Both are empty when the AI gave none.
     */
    public record Found(String company, String alsoKnownAs, String commonName, String ticker,
                        RelationshipType relationship, String provides, String quote, Criticality criticality,
                        String criticalityQuote) {
    }

    /** One stored company name as the AI answered it in the common-names pass. Both fields may be empty. */
    public record CommonNameFound(String name, String commonName, String ticker) { }

    /** A criticality answer matched to an already-known counterparty. */
    public record CriticalityFound(String company, Criticality criticality, String quote) { }

    /** One verdict of the second check, for the claim numbered {@code index} (from 1). */
    public record SecondCheckFound(int index, SecondCheckVerdict verdict, String reason) { }

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
            found.add(new Found(company, text(item, "alsoKnownAs"), text(item, "commonName"), text(item, "ticker"),
                    type, text(item, "provides"), quote, criticality(text(item, "criticality")),
                    text(item, "criticalityQuote")));
        }
        return found;
    }

    /** Reads answers for the criticality-only pass; malformed or empty claims are ignored, not relationship data. */
    public List<CriticalityFound> readCriticalities(String response) {
        JsonNode list;
        try {
            list = mapper.readTree(answerText(response));
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        if (list == null || !list.isArray()) throw new UnreadableAnswerException();
        List<CriticalityFound> found = new ArrayList<>();
        for (JsonNode item : list) {
            String company = text(item, "company");
            Criticality criticality = criticality(text(item, "criticality"));
            String quote = text(item, "criticalityQuote");
            if (!company.isEmpty() && criticality != null) {
                found.add(new CriticalityFound(company, criticality, quote));
            }
        }
        return found;
    }

    /**
     * Reads the common-names pass's answer: a JSON list of {@code { name, commonName, ticker }}. An item with no name
     * is left out; an item with neither a common name nor a ticker says nothing, and is left out too.
     */
    public List<CommonNameFound> readCommonNames(String response) {
        JsonNode list;
        try {
            list = mapper.readTree(answerText(response));
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        if (list == null || !list.isArray()) {
            throw new UnreadableAnswerException();
        }
        List<CommonNameFound> found = new ArrayList<>();
        for (JsonNode item : list) {
            String name = text(item, "name");
            String commonName = text(item, "commonName");
            String ticker = text(item, "ticker");
            if (!name.isEmpty() && !(commonName.isEmpty() && ticker.isEmpty())) {
                found.add(new CommonNameFound(name, commonName, ticker));
            }
        }
        return found;
    }

    /**
     * Reads the second check's answer: a JSON list of {@code { index, verdict, reason }}. An answer that is not a list
     * throws {@link UnreadableAnswerException}. An item with no whole-number index, or a verdict other than AGREES or
     * DOUBTS, is left out, so its claim stays unchecked for the pass to ask about again.
     */
    public List<SecondCheckFound> readSecondChecks(String response) {
        JsonNode list;
        try {
            list = mapper.readTree(answerText(response));
        } catch (JacksonException exception) {
            throw new UnreadableAnswerException();
        }
        if (list == null || !list.isArray()) throw new UnreadableAnswerException();
        List<SecondCheckFound> found = new ArrayList<>();
        for (JsonNode item : list) {
            JsonNode index = item.path("index");
            SecondCheckVerdict verdict = verdict(text(item, "verdict"));
            if (!index.isIntegralNumber() || verdict == null) continue;
            found.add(new SecondCheckFound(index.asInt(), verdict, text(item, "reason")));
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

    private static SecondCheckVerdict verdict(String value) {
        try {
            return value.isEmpty() ? null : SecondCheckVerdict.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Criticality criticality(String value) {
        try {
            return value.isEmpty() ? null : Criticality.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
