package com.truesight.ask;

import com.jayway.jsonpath.JsonPath;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.news.Alert;
import com.truesight.news.AlertHolding;
import com.truesight.news.AlertHoldingRepository;
import com.truesight.news.AlertRepository;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.NewsArticle;
import com.truesight.news.NewsArticleRepository;
import com.truesight.news.NewsAssessment;
import com.truesight.news.NewsAssessmentRepository;
import com.truesight.news.Severity;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.FakeAi;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ask TrueSight against a real database, with Gemini replaced by the real answers saved in docs/examples/ (see its
 * README, "The Ask TrueSight Answers"): one test per Acceptance Criterion, plus the rules its Technical Notes add.
 *
 * <p>The portfolio is the sample one: ten holdings and the 17 real relationships, seeded in the order of the sample files,
 * so the ids in the saved answers ([H1], [R3] ...) mean what they meant when Gemini gave them. Each test makes its own
 * companies, with a unique suffix, so nothing another test leaves behind reaches this portfolio.
 */
@IntegrationTest
class AskIT {

    /** A real headline from the Google News sample, docs/examples/google-news-tsmc.xml. */
    private static final String HEADLINE =
            "TSMC expected to report sixth straight quarter of record profit thanks to AI boom";
    private static final String QUESTION = "Which of my holdings depend on Taiwan Semiconductor, and have those links been reviewed?";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired AlertRepository alerts;
    @Autowired AlertHoldingRepository alertHoldings;

    @MockitoBean GeminiClient gemini;

    private String suffix;
    private String email;

    @BeforeEach
    void startFresh() {
        reset(gemini);
        suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "ask-" + UUID.randomUUID() + "@example.com";
    }

    // ---- 1. A question about the portfolio is answered with sources that open their items ---------------------------

    @Test
    void aQuestionAboutThePortfolioIsAnsweredWithSourcesThatAreItsOwnItems() throws Exception {
        Sample sample = seedSample(17);
        gemini(Examples.geminiAskAnswerable());

        String body = ask(sample.portfolio, email, QUESTION).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.answer")).contains("Taiwan Semiconductor Manufacturing");
        List<Map<String, Object>> sources = JsonPath.read(body, "$.sources");
        assertThat(sources).hasSize(10);
        // The relationships Gemini cited ([R1], [R3], [R5], [R10], [R14]) and the holdings ([H1], [H3], [H5], [H6], [H7]),
        // by the ids the website opens them with.
        assertThat(idsOf(sources, "RELATIONSHIP")).containsExactly(
                sample.relationshipId(1), sample.relationshipId(3), sample.relationshipId(5),
                sample.relationshipId(10), sample.relationshipId(14));
        assertThat(idsOf(sources, "HOLDING")).containsExactly(
                sample.holdingId(1), sample.holdingId(3), sample.holdingId(5), sample.holdingId(6), sample.holdingId(7));
        assertThat(sources).allSatisfy(source -> assertThat((String) source.get("label")).isNotBlank());
        // Each source is one of the portfolio's own relationships and holdings, as the website lists them.
        String itsRelationships = get("/api/portfolios/{id}/relationships", sample.portfolio);
        String itsHoldings = get("/api/portfolios/{id}/holdings", sample.portfolio);
        List<Integer> relationshipIds = JsonPath.read(itsRelationships, "$[*].id");
        List<Integer> holdingIds = JsonPath.read(itsHoldings, "$[*].id");
        for (Long id : idsOf(sources, "RELATIONSHIP")) {
            assertThat(relationshipIds).contains(id.intValue());
        }
        for (Long id : idsOf(sources, "HOLDING")) {
            assertThat(holdingIds).contains(id.intValue());
        }
    }

    @Test
    void anOpenAlertIsCitedByItsLeadAlertIdAndOpensAsThatEvent() throws Exception {
        Sample sample = seedSample(17);
        Alert alert = seedAlert(sample, 1);
        // Written by hand: the sample has no alert, so no real Gemini answer cites one. It holds only the id cited.
        gemini(envelope("{\"answer\":\"One open alert reaches your holdings.\",\"sourceIds\":[\"A1\"]}"));

        String body = ask(sample.portfolio, email, "Which alerts are open?").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        List<Map<String, Object>> sources = JsonPath.read(body, "$.sources");
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0)).containsEntry("kind", "ALERT").containsEntry("label", HEADLINE.length() <= 80
                ? HEADLINE : HEADLINE.substring(0, 79).stripTrailing() + "…");
        assertThat(((Number) sources.get(0).get("id")).longValue()).isEqualTo(alert.getId());
        assertThat(promptSent()).contains("[A1] High severity production stop. Headline: \"" + HEADLINE + "\"");
    }

    // ---- 2. A question the data cannot answer ----------------------------------------------------------------------

    @Test
    void aQuestionTheDataCannotAnswerGetsTheUnknownSentenceAndNoSources() throws Exception {
        Sample sample = seedSample(17);
        gemini(Examples.geminiAskUnknown());

        ask(sample.portfolio, email, "What was NVIDIA's revenue last quarter?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("TrueSight's data does not answer that."))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    // ---- 3. A source that is not in the portfolio's data is dropped -------------------------------------------------

    @Test
    void aSourceNotInThePortfoliosDataIsDroppedAndTheRestKept() throws Exception {
        // Only the first five relationships exist, so the answer's [R10] and [R14] name nothing in this portfolio.
        Sample sample = seedSample(5);
        gemini(Examples.geminiAskAnswerable());

        String body = ask(sample.portfolio, email, QUESTION).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        List<Map<String, Object>> sources = JsonPath.read(body, "$.sources");
        assertThat(idsOf(sources, "RELATIONSHIP")).containsExactly(
                sample.relationshipId(1), sample.relationshipId(3), sample.relationshipId(5));
        assertThat(idsOf(sources, "HOLDING")).hasSize(5);
    }

    @Test
    void anAnswerLeftWithNoSourceIsReplacedByTheUnknownSentence() throws Exception {
        // A portfolio with nothing in it: every id the answer names is dropped.
        Portfolio empty = portfolios.save(new Portfolio(logins.user(email).getId(), "Empty"));
        gemini(Examples.geminiAskAnswerable());

        ask(empty, email, QUESTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("TrueSight's data does not answer that."))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    // ---- 4. An answer resting on unreviewed relationships says so ---------------------------------------------------

    @Test
    void anAnswerRestingOnUnreviewedRelationshipsSaysSo() throws Exception {
        Sample sample = seedSample(17);
        gemini(Examples.geminiAskAnswerable());

        String body = ask(sample.portfolio, email, QUESTION).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        // The records told Gemini each relationship is unreviewed, and its real answer says so.
        assertThat(promptSent()).contains("Unreviewed. Medium confidence.");
        assertThat(((String) JsonPath.read(body, "$.answer")).toLowerCase()).contains("unreviewed");
    }

    @Test
    void anAnswerThatForgetsToSayItRestsOnUnreviewedRelationshipsIsGivenTheNote() throws Exception {
        Sample sample = seedSample(17);
        // Written by hand: an answer that leaves out the review status, which Gemini's real answer here does not.
        gemini(envelope("{\"answer\":\"Taiwan Semiconductor Manufacturing supplies NVIDIA.\",\"sourceIds\":[\"R1\"]}"));

        ask(sample.portfolio, email, "Who supplies NVIDIA?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Taiwan Semiconductor Manufacturing supplies NVIDIA. "
                        + AskGuards.UNREVIEWED_NOTE));
    }

    @Test
    void anApprovedRelationshipIsSentToGeminiAsApprovedAndNeedsNoNote() throws Exception {
        Sample sample = seedSample(17);
        Relationship first = relationships.findById(sample.relationshipId(1)).orElseThrow();
        first.setReviewStatus(com.truesight.relationship.ReviewStatus.APPROVED);
        relationships.save(first);
        gemini(envelope("{\"answer\":\"Taiwan Semiconductor Manufacturing supplies NVIDIA.\",\"sourceIds\":[\"R1\"]}"));

        ask(sample.portfolio, email, "Who supplies NVIDIA?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Taiwan Semiconductor Manufacturing supplies NVIDIA."));
        assertThat(promptSent()).contains("Approved");
    }

    // ---- 5. A question asking what to buy or sell -------------------------------------------------------------------

    @Test
    void aQuestionAskingWhatToBuyGetsTheNoAdviceSentenceNotARecommendation() throws Exception {
        Sample sample = seedSample(17);
        gemini(Examples.geminiAskAdvice());

        String body = ask(sample.portfolio, email, "Should I buy more AMD?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskGuards.NO_ADVICE))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("buy").doesNotContainIgnoringCase("sell");
    }

    @Test
    void anAnswerThatDoesRecommendATradeIsReplacedByTheNoAdviceSentence() throws Exception {
        Sample sample = seedSample(17);
        // Written by hand: what the check must catch if Gemini ever did recommend a trade.
        gemini(envelope("{\"answer\":\"You should buy more of it.\",\"sourceIds\":[\"R3\"]}"));

        ask(sample.portfolio, email, "Should I buy more AMD?")
                .andExpect(jsonPath("$.answer").value(AskGuards.NO_ADVICE))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    // ---- 6. Another user's portfolio; the question reaches Gemini as an ASK call ------------------------------------

    @Test
    void anotherUsersPortfolioIsNotFoundAndGeminiIsNeverAsked() throws Exception {
        Sample sample = seedSample(17);

        ask(sample.portfolio, "someone-else-" + UUID.randomUUID() + "@example.com", QUESTION)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        ask(Long.MAX_VALUE, email, QUESTION).andExpect(status().isNotFound());

        verifyNoInteractions(gemini);
    }

    @Test
    void theQuestionIsSentToGeminiAsAnAskCallWithTheConversationButNothingFromAnotherPortfolio() throws Exception {
        Sample sample = seedSample(17);
        // Another user's portfolio, whose data must never reach this prompt.
        Portfolio other = portfolios.save(new Portfolio(logins.user("other-" + UUID.randomUUID() + "@example.com").getId(), "Other"));
        Holding secret = new Holding(other.getId(), "ZZZZ", new BigDecimal("100"));
        holdings.save(secret);
        gemini(Examples.geminiAskUnknown());

        mockMvc.perform(post("/api/portfolios/{id}/questions", sample.portfolio.getId())
                        .header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"And the second one?\",\"history\":[{\"question\":\"Who supplies AMD?\","
                                + "\"answer\":\"Two foundries do.\"}]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(1)).generate(request.capture(), any());
        assertThat(request.getValue().purpose()).isEqualTo(AiPurpose.ASK);
        assertThat(request.getValue().prompt()).contains("And the second one?", "Q: Who supplies AMD?\nA: Two foundries do.",
                "[H1] Advanced Micro Devices", "[R1] ");
        assertThat(request.getValue().prompt()).doesNotContain("ZZZZ");
    }

    // ---- The request, and Gemini failing -----------------------------------------------------------------------------

    @Test
    void aQuestionOverFiveHundredCharactersIsRefused() throws Exception {
        Sample sample = seedSample(17);

        ask(sample.portfolio, email, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("question must be 500 characters or fewer."));
        verifyNoInteractions(gemini);
    }

    @Test
    void aBlankQuestionAndMoreThanSixEarlierTurnsAreRefused() throws Exception {
        Sample sample = seedSample(17);

        ask(sample.portfolio, email, "   ").andExpect(status().isBadRequest());
        StringBuilder turns = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            turns.append(i > 0 ? "," : "").append("{\"question\":\"Q").append(i).append("\",\"answer\":\"A\"}");
        }
        mockMvc.perform(post("/api/portfolios/{id}/questions", sample.portfolio.getId())
                        .header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Who?\",\"history\":[" + turns + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }

    @Test
    void askingNeedsALogin() throws Exception {
        Sample sample = seedSample(17);

        mockMvc.perform(post("/api/portfolios/{id}/questions", sample.portfolio.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Who?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenTheAiCannotBeReachedTheUserIsToldSoWithAReadableMessage() throws Exception {
        Sample sample = seedSample(17);
        when(gemini.generate(any(), any()))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));

        ask(sample.portfolio, email, QUESTION)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("The AI could not be reached. Try again later."));
    }

    @Test
    void anAnswerThatCannotBeReadIsRefusedWithAReadableMessage() throws Exception {
        Sample sample = seedSample(17);
        gemini(envelope("this is not the JSON that was asked for"));

        ask(sample.portfolio, email, QUESTION)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("The AI's answer could not be read. Try again."));
    }

    // ---- Seeding and asking ---------------------------------------------------------------------------------------

    /** The seeded portfolio, and the ids the database gave its relationships and holdings, in the order of the records. */
    private record Sample(Portfolio portfolio, List<Relationship> relationships, List<Holding> holdingsByTicker) {
        Long relationshipId(int number) {
            return relationships.get(number - 1).getId();
        }

        Long holdingId(int number) {
            return holdingsByTicker.get(number - 1).getId();
        }
    }

    /**
     * The sample portfolio ("Sample Portfolio", ten holdings of 10%) with the first {@code relationshipCount} of the 17
     * real relationships. Every holding but Sony's has its company and a 10-K or 20-F, as the analysis leaves them.
     */
    private Sample seedSample(int relationshipCount) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Sample Portfolio"));
        List<RelationshipResponse> sampleLinks = AskSample.relationships();
        Map<Long, Company> companies = new HashMap<>();
        Map<Long, Report> reportOf = new HashMap<>();

        List<Holding> seeded = new ArrayList<>();
        for (com.truesight.portfolio.HoldingResponse line : AskSample.holdings()) {
            Holding holding = new Holding(portfolio.getId(), line.ticker(), line.weight());
            holding.setStatus(HoldingStatus.DONE);
            RelationshipResponse.CompanySummary company = sampleLinks.stream()
                    .map(RelationshipResponse::company).filter(found -> line.ticker().equals(found.ticker()))
                    .findFirst().orElse(null);
            if (company != null) {
                Company stored = companyOf(companies, company);
                holding.setCompanyId(stored.getId());
                reportOf.put(company.id(), reports.save(new Report(stored.getId(), line.reportType(), accession(),
                        line.reportDate(), "https://www.sec.gov/", "Set by the test: the report's text is not used.")));
            }
            seeded.add(holdings.save(holding));
        }
        seeded.sort(Comparator.comparing(Holding::getTicker));

        List<Relationship> links = new ArrayList<>();
        for (RelationshipResponse link : sampleLinks.subList(0, relationshipCount)) {
            links.add(relationships.save(new Relationship(reportOf.get(link.company().id()).getId(),
                    companyOf(companies, link.company()).getId(), companyOf(companies, link.counterparty()).getId(),
                    link.type(), link.provides(), link.evidence())));
        }
        return new Sample(portfolio, links, seeded);
    }

    private Company companyOf(Map<Long, Company> companies, RelationshipResponse.CompanySummary summary) {
        return companies.computeIfAbsent(summary.id(),
                id -> companyDirectory.findOrCreate(summary.name() + " " + suffix, null, null, List.of()));
    }

    private static String accession() {
        return "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /** One open alert about TSMC's headline that reaches the sample's first holding by its first relationship. */
    private Alert seedAlert(Sample sample, int relationshipNumber) {
        Relationship link = sample.relationships.get(relationshipNumber - 1);
        Holding reached = holdings.findByPortfolioIdOrderByTickerAsc(sample.portfolio.getId()).stream()
                .filter(holding -> link.getCompanyId().equals(holding.getCompanyId())).findFirst().orElseThrow();
        NewsArticle article = articles.save(new NewsArticle(link.getCounterpartyId(),
                "https://example.com/" + UUID.randomUUID(), HEADLINE, "Reuters",
                Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS)));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.PRODUCTION_STOP, Severity.HIGH,
                Confidence.MEDIUM, "Set by the test: production stopped.", "Set by the test: the headline reports a stoppage."));
        Alert alert = alerts.saveAndFlush(new Alert(sample.portfolio.getId(), article.getId(), new BigDecimal("10")));
        alertHoldings.save(new AlertHolding(alert.getId(), reached.getId(), link.getId()));
        return alert;
    }

    private void gemini(String response) {
        when(gemini.generate(any(), any())).thenReturn(FakeAi.answer(response));
    }

    /** Gemini's response around one answer text, as the saved examples are. */
    private static String envelope(String answerText) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(answerText) + "}]}}]}";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ResultActions ask(Portfolio portfolio, String asEmail, String question) throws Exception {
        return ask(portfolio.getId(), asEmail, question);
    }

    private ResultActions ask(Long portfolioId, String asEmail, String question) throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/questions", portfolioId)
                .header("Authorization", logins.bearer(asEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":" + quote(question) + "}"));
    }

    private String get(String path, Portfolio portfolio) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path, portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andReturn().getResponse().getContentAsString();
    }

    private String promptSent() {
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generate(request.capture(), any());
        return request.getValue().prompt();
    }

    private static List<Long> idsOf(List<Map<String, Object>> sources, String kind) {
        return sources.stream().filter(source -> kind.equals(source.get("kind")))
                .map(source -> ((Number) source.get("id")).longValue()).toList();
    }
}
