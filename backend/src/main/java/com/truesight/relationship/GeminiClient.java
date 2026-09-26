package com.truesight.relationship;

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
import java.util.List;
import java.util.Map;

/**
 * The only class that talks to Gemini. It sends one prompt and returns Gemini's whole answer, unread; reading it is
 * {@link GeminiAnswer}'s job. Tests replace this class with a fake, so they never call Gemini.
 *
 * <p>The model comes from {@code GEMINI_MODEL} and the key from {@code GEMINI_API_KEY}. The request asks for JSON only,
 * in the shape of {@link #RESPONSE_SCHEMA}, at temperature 0 so the same report gives the same answer.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    /** A list of { company, alsoKnownAs, relationship, provides, quote }, with relationship SUPPLIER or CUSTOMER. */
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "company", Map.of("type", "STRING"),
                            "alsoKnownAs", Map.of("type", "STRING"),
                            "relationship", Map.of("type", "STRING", "enum", List.of("SUPPLIER", "CUSTOMER")),
                            "provides", Map.of("type", "STRING"),
                            "quote", Map.of("type", "STRING")),
                    "required", List.of("company", "alsoKnownAs", "relationship", "provides", "quote"),
                    "propertyOrdering", List.of("company", "alsoKnownAs", "relationship", "provides", "quote")));

    private final RestClient client;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiClient(@Value("${truesight.gemini.api-key}") String apiKey,
                        @Value("${truesight.gemini.model}") String model) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey, model);
    }

    /** For tests, which pass a builder bound to a fake server. */
    GeminiClient(RestClient.Builder builder, String apiKey, String model) {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Sends the prompt and returns Gemini's answer exactly as it arrived.
     *
     * @throws GeminiException with a reason a user can read, if there is no key or Gemini cannot answer
     */
    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("The AI is not set up: there is no Gemini API key.", null);
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA));
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
