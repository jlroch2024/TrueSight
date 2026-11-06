package com.truesight.ask;

import com.jayway.jsonpath.JsonPath;
import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.TestGemini;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each question appears in the AI Audit Trail as purpose ASK. {@code AskIT} replaces the whole Gemini client, which
 * records nothing, so this test keeps the real client and the real Audit Trail and replaces only the Gemini server,
 * with the real answer saved in docs/examples/.
 */
@IntegrationTest
@Import(AskAuditIT.FakeGeminiServer.class)
class AskAuditIT {

    /** The Gemini server, which the real client of this test talks to. */
    @TestConfiguration
    static class FakeGeminiServer {
        static final RestClient.Builder BUILDER = RestClient.builder();
        static final MockRestServiceServer SERVER = MockRestServiceServer.bindTo(BUILDER).build();

        @Bean
        @Primary
        GeminiClient geminiOverAFakeServer(AiCallLog log) {
            return TestGemini.client(BUILDER, log);
        }
    }

    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired AiCallRepository calls;

    @BeforeEach
    void resetTheServer() {
        FakeGeminiServer.SERVER.reset();
    }

    @Test
    void aQuestionAppearsInTheAuditTrailAsAnAskCallWithItsPromptAndAnswer() throws Exception {
        String email = "ask-audit-" + UUID.randomUUID() + "@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Audit"));
        holdings.save(new Holding(portfolio.getId(), "AUDT", new BigDecimal("100")));
        String question = "What was NVIDIA's revenue last quarter? " + UUID.randomUUID();
        FakeGeminiServer.SERVER.expect(anything())
                .andRespond(withSuccess(Examples.geminiAskUnknown(), MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/portfolios/{id}/questions", portfolio.getId())
                        .header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("TrueSight's data does not answer that."));

        AiCall recorded = calls.findAll().stream()
                .filter(call -> call.getPrompt() != null && call.getPrompt().contains(question)).findFirst().orElseThrow();
        assertThat(recorded.getPurpose()).isEqualTo(AiPurpose.ASK);
        assertThat(recorded.getOutcome()).isEqualTo(AiOutcome.ANSWERED);
        // The Audit Trail shows exactly what was sent: the rules, this portfolio's records, and the question.
        assertThat(recorded.getPrompt()).contains("Rules:", "Holdings (1):", "[H1] AUDT, 100% of the portfolio.");

        // An analyst finds it in the Audit Trail, filtered by purpose, and can open it.
        String analyst = logins.analystBearer("ask-audit-analyst-" + UUID.randomUUID() + "@example.com");
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String listed = mockMvc.perform(get("/api/ai-calls?purpose=ASK&from={day}&to={day}", today, today)
                        .header("Authorization", analyst))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(listed, "$.calls[*].id");
        assertThat(ids).contains(recorded.getId().intValue());
        mockMvc.perform(get("/api/ai-calls/{id}", recorded.getId()).header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("ASK"))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString(question)));
        FakeGeminiServer.SERVER.verify();
    }
}
