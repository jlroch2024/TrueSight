package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Checks what is sent to Gemini, and what is recorded about each call, against a fake server: no request leaves the
 * computer. The AI Audit Trail is a stand-in here, so each test can see exactly what was recorded.
 */
class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent";

    /** A real sentence from NVIDIA's 10-K in docs/examples, standing in for a report's text. */
    private static final String REPORT_TEXT = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AiCallLog calls = mock(AiCallLog.class);

    private GeminiClient client(String key) {
        return new GeminiClient(builder, key, "gemini-test-model", calls);
    }

    /** A request whose prompt quotes a report, as finding relationships does. */
    private static AiRequest reportRequest() {
        return new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "Find suppliers in: " + REPORT_TEXT, 12L,
                "Find suppliers in: " + AiRequest.reportMarker(12L, REPORT_TEXT));
    }

    @Test
    void asksTheConfiguredModelForJsonOnlyWithTheKeyInAHeader() {
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

        AiAnswer answer = client("test-key").generate(AiRequest.of(AiPurpose.FIND_RELATIONSHIPS, "The prompt."));

        assertThat(answer.text()).isEqualTo(Examples.geminiNvidia());
        server.verify();
    }

    @Test
    void theSecondCheckAsksForAnIndexAVerdictOfAgreesOrDoubtsAndAReasonForEachClaim() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("The claims."))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("ARRAY"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.properties.index.type")
                        .value("INTEGER"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.properties.verdict.enum[0]")
                        .value("AGREES"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.properties.verdict.enum[1]")
                        .value("DOUBTS"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.properties.reason.type")
                        .value("STRING"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        AiAnswer answer = client("test-key").generateSecondCheck(AiRequest.of(AiPurpose.SECOND_CHECK, "The claims."));

        assertThat(answer.text()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void acceptsACallerProvidedResponseSchema() {
        Map<String, Object> schema = Map.of("type", "ARRAY", "items", Map.of("type", "STRING"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.responseSchema.items.type").value("STRING"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        AiAnswer answer = client("test-key").generate(AiRequest.of(AiPurpose.JUDGE_NEWS, "News prompt"), schema);

        assertThat(answer.text()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void anAnswerIsRecordedOnceAsAnsweredWithItsPurposeAndTheReportsTextLeftOut() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Find suppliers in: " + REPORT_TEXT))
                .andRespond(withSuccess(Examples.geminiNvidia(), MediaType.APPLICATION_JSON));
        when(calls.record(any(), anyString(), any(), any(), any(), any(), anyInt())).thenReturn(7L);

        AiAnswer answer = client("test-key").generate(reportRequest());

        ArgumentCaptor<AiRequest> recorded = ArgumentCaptor.forClass(AiRequest.class);
        verify(calls, times(1)).record(recorded.capture(), eq("gemini-test-model"), eq(Examples.geminiNvidia()),
                eq(AiOutcome.ANSWERED), isNull(), any(Instant.class), anyInt());
        assertThat(recorded.getValue().purpose()).isEqualTo(AiPurpose.FIND_RELATIONSHIPS);
        assertThat(recorded.getValue().reportId()).isEqualTo(12L);
        assertThat(recorded.getValue().recordedPrompt())
                .isEqualTo("Find suppliers in: [report 12: " + REPORT_TEXT.length() + " characters]")
                .doesNotContain("TSMC");
        assertThat(answer.callId()).isEqualTo(7L);
    }

    @Test
    void anErrorFromGeminiGivesAReasonAUserCanReadAndIsRecordedOnceAsFailed() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client("test-key").generate(reportRequest()))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessage("The AI returned an error (HTTP 503). Try again later.");

        ArgumentCaptor<AiRequest> recorded = ArgumentCaptor.forClass(AiRequest.class);
        verify(calls, times(1)).record(recorded.capture(), eq("gemini-test-model"), isNull(), eq(AiOutcome.FAILED),
                eq("The AI returned an error (HTTP 503). Try again later."), any(Instant.class), anyInt());
        assertThat(recorded.getValue().recordedPrompt()).doesNotContain("TSMC");
    }

    @Test
    void withNoKeyNothingIsSentAndTheCallIsRecordedOnceAsFailedWithoutAnyKey() {
        assertThatThrownBy(() -> client("").generate(AiRequest.of(AiPurpose.JUDGE_NEWS, "The prompt.")))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessage("The AI is not set up: there is no Gemini API key.");

        server.verify();
        verify(calls, times(1)).record(any(), eq("gemini-test-model"), isNull(), eq(AiOutcome.FAILED),
                eq("The AI is not set up: there is no Gemini API key."), any(Instant.class), anyInt());
    }

    @Test
    void theApiKeyIsNeverRecorded() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client("secret-test-key").generate(reportRequest()))
                .isInstanceOf(GeminiClient.GeminiException.class);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(calls).record(request.capture(), anyString(), any(), any(), error.capture(), any(), anyInt());
        assertThat(error.getValue()).doesNotContain("secret-test-key");
        assertThat(request.getValue().recordedPrompt()).doesNotContain("secret-test-key");
    }
}
