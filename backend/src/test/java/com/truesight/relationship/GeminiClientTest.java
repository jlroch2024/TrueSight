package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks what is sent to Gemini, against a fake server: no request leaves the computer. */
class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void asksTheConfiguredModelForJsonOnlyWithTheKeyInAHeader() {
        GeminiClient client = new GeminiClient(builder, "test-key", "gemini-test-model");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("The prompt."))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("ARRAY"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.properties.relationship.enum[1]")
                        .value("CUSTOMER"))
                .andRespond(withSuccess(Examples.geminiNvidia(), MediaType.APPLICATION_JSON));

        assertThat(client.generate("The prompt.")).isEqualTo(Examples.geminiNvidia());
        server.verify();
    }

    @Test
    void anErrorFromGeminiGivesAReasonAUserCanRead() {
        GeminiClient client = new GeminiClient(builder, "test-key", "gemini-test-model");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.generate("The prompt."))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessage("The AI returned an error (HTTP 503). Try again later.");
    }

    @Test
    void withNoKeyNothingIsSent() {
        GeminiClient client = new GeminiClient(builder, "", "gemini-test-model");

        assertThatThrownBy(() -> client.generate("The prompt."))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessage("The AI is not set up: there is no Gemini API key.");
        server.verify();
    }
}
