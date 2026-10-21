package com.truesight.news;

import com.truesight.company.Company;
import static com.truesight.support.FakeAi.answer;
import com.truesight.ai.AiRequest;
import com.truesight.ai.AiAnswer;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.NewsStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter Out Noise through the whole app: Check News runs the {@link NewsAssessor} step, which judges each new article
 * once. Gemini is replaced by a fake that gives {@link Examples#geminiNewsAssessment()}, its real answer for the first
 * 20 headlines of {@link Examples#googleNewsTsmc()}, so tests never call Gemini. Google News is replaced too: these
 * tests save the sample's articles themselves, in the order Gemini was sent them.
 */
@IntegrationTest
class NewsAssessorIT {

    private static final String TSMC_CIK = "0001046179";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;

    @MockitoBean NewsClient news;
    @MockitoBean GeminiClient gemini;

    private Company tsmc;

    @BeforeEach
    void startFromTsmcWithNoArticles() {
        reset(news, gemini);
        when(news.search(anyString())).thenReturn(List.of());
        when(gemini.generate(any(AiRequest.class), any())).thenReturn(answer(Examples.geminiNewsAssessment()));
        tsmc = companyDirectory.findOrCreate("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK, "TSM",
                List.of());
        articles.deleteByCompanyId(tsmc.getId()); // its assessments go with it
    }

    @Test
    void eachNewArticleGetsExactlyOneAssessmentAndCheckingAgainDoesNotAskGeminiAgain() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        Portfolio portfolio = analysedPortfolio("assess-once@example.com");

        checkNews(portfolio, "assess-once@example.com", NewsStatus.IDLE);
        checkNews(portfolio, "assess-once@example.com", NewsStatus.IDLE);

        assertThat(assessmentsOf(saved)).hasSize(20)
                .extracting(NewsAssessment::getArticleId).doesNotHaveDuplicates();
        verify(gemini, times(1)).generate(any(AiRequest.class), any());
    }

    @Test
    void anArticleJudgedForOnePortfolioIsSharedWithAnotherWithoutAskingGeminiAgain() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        Portfolio first = analysedPortfolio("assess-shared-a@example.com");
        Portfolio second = analysedPortfolio("assess-shared-b@example.com");

        checkNews(first, "assess-shared-a@example.com", NewsStatus.IDLE);
        checkNews(second, "assess-shared-b@example.com", NewsStatus.IDLE);

        assertThat(assessmentsOf(saved)).hasSize(20);
        verify(gemini, times(1)).generate(any(AiRequest.class), any());
    }

    @Test
    void everyHeadlineInTheSampleGetsTheJudgementInTheSavedGeminiAnswer() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        Portfolio portfolio = analysedPortfolio("assess-sample@example.com");

        checkNews(portfolio, "assess-sample@example.com", NewsStatus.IDLE);

        JsonNode answers = savedAnswers();
        assertThat(answers).hasSize(20);
        Map<Long, NewsAssessment> byArticle = assessmentsByArticle(saved);
        for (JsonNode answer : answers) {
            NewsArticle article = saved.get(answer.path("index").asInt() - 1);
            NewsAssessment assessment = byArticle.get(article.getId());
            assertThat(assessment.isEvent()).isEqualTo(answer.path("isEvent").asBoolean());
            assertThat(assessment.getEventType()).isNull(); // every sample headline is noise
            assertThat(assessment.getSeverity()).isNull();
            assertThat(assessment.getConfidence().name()).isEqualTo(answer.path("confidence").asString());
            assertThat(assessment.getSummary()).isNull();
            assertThat(assessment.getReason()).isEqualTo(answer.path("reason").asString());
        }
    }

    @Test
    void theHeadlinesAreSentNumberedFromOneWithTheirCompanyUsingThePromptAndSchema() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        Portfolio portfolio = analysedPortfolio("assess-prompt@example.com");

        checkNews(portfolio, "assess-prompt@example.com", NewsStatus.IDLE);

        ArgumentCaptor<AiRequest> prompt = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generate(prompt.capture(), eq(NewsAssessor.NEWS_SCHEMA));
        assertThat(prompt.getValue().prompt()).startsWith(NewsAssessor.PROMPT);
        for (int i = 0; i < saved.size(); i++) {
            assertThat(prompt.getValue().prompt())
                    .contains("\n" + (i + 1) + ". [Taiwan Semiconductor Manufacturing] " + saved.get(i).getTitle() + "\n");
        }
    }

    @Test
    void moreThanTwentyNewArticlesAreSentInBatchesOfUpToTwenty() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(25);
        Portfolio portfolio = analysedPortfolio("assess-batches@example.com");

        checkNews(portfolio, "assess-batches@example.com", NewsStatus.IDLE);

        ArgumentCaptor<AiRequest> prompts = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(2)).generate(prompts.capture(), any());
        assertThat(prompts.getAllValues().get(0).prompt()).contains("\n20. [").doesNotContain("\n21. [");
        assertThat(prompts.getAllValues().get(1).prompt()).contains("\n5. [").doesNotContain("\n6. [");
        assertThat(assessmentsOf(saved)).hasSize(25);
    }

    @Test
    void aHeadlineMissingFromGeminisAnswerStaysUnassessedAndIsAssessedByTheNextNewsCheck() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(2);
        Portfolio portfolio = analysedPortfolio("assess-missing@example.com");
        // A hand-made answer that judges headline 1 and leaves out headline 2.
        when(gemini.generate(any(AiRequest.class), any())).thenReturn(gemini(
                "{\"assessments\": [{\"index\": 1, \"isEvent\": false, \"eventType\": \"\", \"severity\": \"\","
                        + " \"confidence\": \"HIGH\", \"summary\": \"\", \"reason\": \"Stock commentary.\"}]}"));

        checkNews(portfolio, "assess-missing@example.com", NewsStatus.IDLE);

        assertThat(assessmentsByArticle(saved)).containsOnlyKeys(saved.get(0).getId());

        when(gemini.generate(any(AiRequest.class), any())).thenReturn(answer(Examples.geminiNewsAssessment()));
        checkNews(portfolio, "assess-missing@example.com", NewsStatus.IDLE);

        assertThat(assessmentsByArticle(saved)).containsOnlyKeys(saved.get(0).getId(), saved.get(1).getId());
        ArgumentCaptor<AiRequest> prompts = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(2)).generate(prompts.capture(), any());
        assertThat(prompts.getAllValues().get(1).prompt())
                .contains("1. [Taiwan Semiconductor Manufacturing] " + saved.get(1).getTitle())
                .doesNotContain(saved.get(0).getTitle());
    }

    @Test
    void anAnswerNamingAnUnknownEventTypeIsStoredAsNoiseThatCouldNotBeUsed() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(1);
        Portfolio portfolio = analysedPortfolio("assess-unknown-type@example.com");
        when(gemini.generate(any(AiRequest.class), any())).thenReturn(gemini(
                "{\"assessments\": [{\"index\": 1, \"isEvent\": true, \"eventType\": \"ALIEN_INVASION\","
                        + " \"severity\": \"HIGH\", \"confidence\": \"HIGH\", \"summary\": \"Something happened.\","
                        + " \"reason\": \"A reason.\"}]}"));

        checkNews(portfolio, "assess-unknown-type@example.com", NewsStatus.IDLE);

        NewsAssessment assessment = assessmentsOf(saved).getFirst();
        assertThat(assessment.isEvent()).isFalse();
        assertThat(assessment.getEventType()).isNull();
        assertThat(assessment.getReason()).isEqualTo("The AI's answer could not be used.");
    }

    @Test
    void anAnswerThatCannotBeReadIsAskedForOnceMore() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        Portfolio portfolio = analysedPortfolio("assess-ask-again@example.com");
        when(gemini.generate(any(AiRequest.class), any()))
                .thenReturn(gemini("Sorry, I cannot help with that."))
                .thenReturn(answer(Examples.geminiNewsAssessment()));

        checkNews(portfolio, "assess-ask-again@example.com", NewsStatus.IDLE);

        verify(gemini, times(2)).generate(any(AiRequest.class), any());
        assertThat(assessmentsOf(saved)).hasSize(20);
    }

    @Test
    void ifGeminiFailsTheArticlesStayUnassessedThePortfolioSaysSoAndTheNextCheckRetriesThem() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        String email = "assess-gemini-fails@example.com";
        Portfolio portfolio = analysedPortfolio(email);
        when(gemini.generate(any(AiRequest.class), any()))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));

        checkNews(portfolio, email, NewsStatus.FAILED);

        assertThat(assessmentsOf(saved)).isEmpty();
        mockMvc.perform(get("/api/portfolios").header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newsStatus").value("FAILED"))
                .andExpect(jsonPath("$[0].newsStatusReason")
                        .value("The AI could not assess the news. Please try again later."));

        reset(gemini);
        when(gemini.generate(any(AiRequest.class), any())).thenReturn(answer(Examples.geminiNewsAssessment()));
        checkNews(portfolio, email, NewsStatus.IDLE);

        assertThat(assessmentsOf(saved)).hasSize(20);
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatusReason()).isNull();
    }

    @Test
    void anAnswerThatStillCannotBeReadWhenAskedAgainIsAFailureToo() throws Exception {
        List<NewsArticle> saved = saveSampleArticles(20);
        String email = "assess-unreadable@example.com";
        Portfolio portfolio = analysedPortfolio(email);
        when(gemini.generate(any(AiRequest.class), any())).thenReturn(gemini("Not JSON."));

        checkNews(portfolio, email, NewsStatus.FAILED);

        verify(gemini, times(2)).generate(any(AiRequest.class), any());
        assertThat(assessmentsOf(saved)).isEmpty();
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatusReason())
                .isEqualTo("The AI could not assess the news. Please try again later.");
    }

    @Test
    void aPortfolioWhoseCompaniesHaveNoNewArticlesDoesNotAskGemini() throws Exception {
        Portfolio portfolio = analysedPortfolio("assess-nothing-new@example.com");

        checkNews(portfolio, "assess-nothing-new@example.com", NewsStatus.IDLE);

        verify(gemini, never()).generate(any(AiRequest.class), any());
    }

    /** The first {@code count} articles of the saved Google News sample, saved for TSMC in order. */
    private List<NewsArticle> saveSampleArticles(int count) {
        List<NewsItem> sample = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        assertThat(sample).hasSizeGreaterThanOrEqualTo(count);
        return sample.subList(0, count).stream()
                .map(item -> articles.save(new NewsArticle(tsmc.getId(), item.url(), item.title(), item.source(),
                        item.publishedAt())))
                .toList();
    }

    private Portfolio analysedPortfolio(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Noise Test"));
        Holding holding = new Holding(portfolio.getId(), "TSM", new BigDecimal("100"));
        holding.setCompanyId(tsmc.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }

    private void checkNews(Portfolio portfolio, String email, NewsStatus expected) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/news-check", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 400; attempt++) {
            if (portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus() == expected) return;
            Thread.sleep(25);
        }
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsStatus()).isEqualTo(expected);
    }

    private List<NewsAssessment> assessmentsOf(List<NewsArticle> saved) {
        return assessments.findByArticleIdIn(saved.stream().map(NewsArticle::getId).toList());
    }

    private Map<Long, NewsAssessment> assessmentsByArticle(List<NewsArticle> saved) {
        return assessmentsOf(saved).stream()
                .collect(java.util.stream.Collectors.toMap(NewsAssessment::getArticleId, a -> a));
    }

    /** The list of 20 judgements inside the saved Gemini answer. */
    private static JsonNode savedAnswers() {
        return JSON.readTree(JSON.readTree(Examples.geminiNewsAssessment())
                .path("candidates").path(0).path("content").path("parts").path(0).path("text").asString());
    }

    /** A whole Gemini API response whose answer text is {@code answer}. */
    private static AiAnswer gemini(String answer) {
        return answer(JSON.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("role", "model", "parts", List.of(Map.of("text", answer))))))));
    }
}
