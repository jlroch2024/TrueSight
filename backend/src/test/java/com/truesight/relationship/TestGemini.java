package com.truesight.relationship;

import com.truesight.ai.AiCallLog;
import org.springframework.web.client.RestClient;

/**
 * A real {@link GeminiClient} that talks to whatever server its builder is bound to. {@code GeminiClient}'s constructor
 * for that is package-private, so tests in other packages (such as Ask TrueSight's, which proves its questions are
 * recorded in the AI Audit Trail) make one here.
 */
public final class TestGemini {

    private TestGemini() {
    }

    public static GeminiClient client(RestClient.Builder builder, AiCallLog log) {
        return new GeminiClient(builder, "test-key", "gemini-test-model", log);
    }
}
