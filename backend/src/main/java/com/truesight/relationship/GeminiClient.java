package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The only class that talks to Gemini. It sends one prompt and returns Gemini's whole answer, unread; reading it is
 * {@link GeminiAnswer}'s job. Tests replace this class with a fake, so they never call Gemini.
 *
 * <p>Every call is recorded in the AI Audit Trail ({@link AiCallLog}), with its purpose, outcome and time taken,
 * whether Gemini answers or fails. The record keeps the request's {@code recordedPrompt}, never a report's text, and
 * never the API key.
 *
 * <p>The model comes from {@code GEMINI_MODEL} and the key from {@code GEMINI_API_KEY}. The request asks for JSON only,
 * in the shape of {@link #RESPONSE_SCHEMA}, at temperature 0 so the same report gives the same answer.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    /** A list of relationship findings, including any report-backed criticality. */
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "company", Map.of("type", "STRING"),
                            "alsoKnownAs", Map.of("type", "STRING"),
                            "commonName", Map.of("type", "STRING"),
                            "ticker", Map.of("type", "STRING"),
                            "relationship", Map.of("type", "STRING", "enum", List.of("SUPPLIER", "CUSTOMER")),
                            "provides", Map.of("type", "STRING"),
                            "quote", Map.of("type", "STRING"),
                            "criticality", Map.of("type", "STRING"),
                            "criticalityQuote", Map.of("type", "STRING")),
                    "required", List.of("company", "alsoKnownAs", "commonName", "ticker", "relationship", "provides",
                            "quote", "criticality", "criticalityQuote"),
                    "propertyOrdering", List.of("company", "alsoKnownAs", "commonName", "ticker", "relationship",
                            "provides", "quote", "criticality", "criticalityQuote")));

    /** For each listed company name: the name echoed back, its common name, and its ticker. */
    private static final Map<String, Object> COMMON_NAMES_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "name", Map.of("type", "STRING"),
                            "commonName", Map.of("type", "STRING"),
                            "ticker", Map.of("type", "STRING")),
                    "required", List.of("name", "commonName", "ticker"),
                    "propertyOrdering", List.of("name", "commonName", "ticker")));

    /** A criticality-only response, matched against known counterparties by name. */
    private static final Map<String, Object> CRITICALITY_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "company", Map.of("type", "STRING"),
                            "criticality", Map.of("type", "STRING"),
                            "criticalityQuote", Map.of("type", "STRING")),
                    "required", List.of("company", "criticality", "criticalityQuote"),
                    "propertyOrdering", List.of("company", "criticality", "criticalityQuote")));

    /** One verdict per numbered claim of the second check, matched to its claim by index. */
    private static final Map<String, Object> SECOND_CHECK_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "index", Map.of("type", "INTEGER"),
                            "verdict", Map.of("type", "STRING", "enum", List.of("AGREES", "DOUBTS")),
                            "reason", Map.of("type", "STRING")),
                    "required", List.of("index", "verdict", "reason"),
                    "propertyOrdering", List.of("index", "verdict", "reason")));

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final AiCallLog calls;

    @Autowired
    public GeminiClient(@Value("${truesight.gemini.api-key}") String apiKey,
                        @Value("${truesight.gemini.model}") String model, AiCallLog calls) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey, model, calls);
    }

    /** For tests, which pass a builder bound to a fake server. */
    GeminiClient(RestClient.Builder builder, String apiKey, String model, AiCallLog calls) {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.calls = calls;
    }

    /**
     * Asks for relationships in the shape of {@link #RESPONSE_SCHEMA}, and returns Gemini's answer exactly as it
     * arrived, with the id of its record.
     *
     * @throws GeminiException with a reason a user can read, if there is no key or Gemini cannot answer
     */
    public AiAnswer generate(AiRequest request) {
        return generate(request, RESPONSE_SCHEMA);
    }

    /** Asks for criticality-only answers, matched to previously found counterparties. */
    public AiAnswer generateCriticality(AiRequest request) {
        return generate(request, CRITICALITY_SCHEMA);
    }

    /** Asks for each listed company's common name and ticker, in the shape of {@link #COMMON_NAMES_SCHEMA}. */
    public AiAnswer generateCommonNames(AiRequest request) {
        return generate(request, COMMON_NAMES_SCHEMA);
    }

    /** Asks for a verdict, Agrees or Doubts with a reason, on each numbered claim of the second check. */
    public AiAnswer generateSecondCheck(AiRequest request) {
        return generate(request, SECOND_CHECK_SCHEMA);
    }

    /** As {@link #generate(AiRequest)}, with the caller's JSON response schema. */
    public AiAnswer generate(AiRequest request, Map<String, Object> responseSchema) {
        Instant startedAt = Instant.now();
        long started = System.nanoTime();
        try {
            String text = ask(request.prompt(), responseSchema);
            Long callId = record(request, text, AiOutcome.ANSWERED, null, startedAt, since(started));
            return new AiAnswer(text, callId);
        } catch (GeminiException exception) {
            record(request, null, AiOutcome.FAILED, exception.getMessage(), startedAt, since(started));
            throw exception;
        }
    }

    /**
     * Saves the call's row, then names its report from the caller's transaction, which can see a report it is still
     * saving. A record that cannot be saved is logged and skipped: the audit trail must never stop the work itself.
     */
    private Long record(AiRequest request, String text, AiOutcome outcome, String error, Instant startedAt,
                        int durationMs) {
        try {
            Long callId = calls.record(request, model, text, outcome, error, startedAt, durationMs);
            calls.attachReport(callId, request.reportId());
            return callId;
        } catch (RuntimeException exception) {
            log.warn("Could not record a {} call to Gemini in the audit trail.", request.purpose(), exception);
            return null;
        }
    }

    private static int since(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private String ask(String prompt, Map<String, Object> responseSchema) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("The AI is not set up: there is no Gemini API key.", null);
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
        log.info("Asking Gemini ({}) with a {}-character prompt.", model, prompt.length());
        try {
            return client.post()
                    .uri(URL, model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new GeminiException("The AI returned an error (HTTP %d). Try again later."
                    .formatted(exception.getStatusCode().value()), exception);
        } catch (ResourceAccessException exception) {
            throw new GeminiException("The AI could not be reached. Try again later.", exception);
        } catch (RestClientException exception) {
            throw new GeminiException("The AI could not be asked. Try again later.", exception);
        }
    }

    /** Gemini reads a long report before answering, so it is given two minutes. */
    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofMinutes(2));
        return factory;
    }

    /** The AI failed. The message says why, in words a user can read, and is saved as the holding's reason. */
    public static class GeminiException extends RuntimeException {
        public GeminiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
