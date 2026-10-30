package com.truesight.news;

import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.support.Examples;
import com.truesight.support.FakeAi;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recommendation generation against PostgreSQL, using Gemini's real saved answer instead of an external call. */
@IntegrationTest
class RecommendationWriterIT {
    @Autowired RecommendationWriter writer;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired AlertRepository alerts;
    @Autowired RecommendationRepository recommendations;

    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void prepareGemini() {
        reset(gemini);
        when(gemini.generate(any(), any())).thenReturn(FakeAi.answer(Examples.geminiRecommendation()));
    }

    @Test
    void aRealRecommendationIsSavedOnceForAnEventSharedAcrossPortfolios() {
        Event event = event();
        Portfolio first = portfolio();
        Portfolio second = portfolio();
        alerts.save(new Alert(first.getId(), event.article().getId(), BigDecimal.TEN));
        alerts.save(new Alert(second.getId(), event.article().getId(), BigDecimal.TEN));

        writer.run(first.getId());
        writer.run(first.getId());
        writer.run(second.getId());

        Recommendation saved = recommendations.findByArticleId(event.article().getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(saved.getText()).isEqualTo("Monitor updates from Nvidia and its regional distribution partners "
                + "regarding freight security protocols in Latin America to verify whether additional transit "
                + "disruptions or shipment delays affect RTX 5090 retail availability.");
        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(1)).generate(request.capture(), any());
        assertThat(request.getValue().purpose()).isEqualTo(com.truesight.ai.AiPurpose.RECOMMEND);
    }

    @Test
    void aForbiddenRecommendationIsWithheldAutomatically() {
        Event event = event();
        Portfolio portfolio = portfolio();
        alerts.save(new Alert(portfolio.getId(), event.article().getId(), BigDecimal.TEN));
        when(gemini.generate(any(), any())).thenReturn(FakeAi.answer(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"nextStep\\\":\\\"Buy the product.\\\"}\"}]}}]}"));

        writer.run(portfolio.getId());

        Recommendation saved = recommendations.findByArticleId(event.article().getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RecommendationStatus.WITHHELD);
        assertThat(saved.getNote()).isEqualTo("Read as investment advice.");
    }

    @Test
    void failedGeminiLeavesTheAlertAndTheNextCheckRetriesTheSuggestion() {
        Event event = event();
        Portfolio portfolio = portfolio();
        Alert alert = alerts.save(new Alert(portfolio.getId(), event.article().getId(), BigDecimal.TEN));
        when(gemini.generate(any(), any()))
                .thenThrow(new GeminiClient.GeminiException("temporary failure", null))
                .thenReturn(FakeAi.answer(Examples.geminiRecommendation()));

        writer.run(portfolio.getId());
        assertThat(alerts.findById(alert.getId())).isPresent();
        assertThat(recommendations.findByArticleId(event.article().getId())).isEmpty();

        writer.run(portfolio.getId());

        assertThat(recommendations.findByArticleId(event.article().getId())).isPresent();
        verify(gemini, times(2)).generate(any(com.truesight.ai.AiRequest.class), any());
    }

    @Test
    void threeArticlesAboutOneEventAskGeminiOnceAndSaveOneSuggestion() {
        Portfolio portfolio = portfolio();
        Company company = company();
        Instant now = Instant.now();
        List<NewsArticle> eventArticles = List.of(
                alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofHours(1))),
                alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofHours(30))),
                alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofHours(60))));

        writer.run(portfolio.getId());

        verify(gemini, times(1)).generate(any(AiRequest.class), any());
        assertThat(recommendations.findByArticleIdIn(eventArticles.stream().map(NewsArticle::getId).toList()))
                .hasSize(1);
    }

    @Test
    void aLaterArticleOfAnEventAlreadyHavingASuggestionGetsNoneAndGeminiIsNotAskedAgain() {
        Portfolio portfolio = portfolio();
        Company company = company();
        Instant now = Instant.now();
        alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofHours(30)));
        writer.run(portfolio.getId());
        NewsArticle later = alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofHours(1)));

        writer.run(portfolio.getId());

        verify(gemini, times(1)).generate(any(AiRequest.class), any());
        assertThat(recommendations.findByArticleId(later.getId())).isEmpty();
    }

    @Test
    void twoArticlesPublishedFiveDaysApartAreTwoEventsWithOneSuggestionEach() {
        Portfolio portfolio = portfolio();
        Company company = company();
        Instant now = Instant.now();
        NewsArticle first = alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofDays(6)));
        NewsArticle second = alerted(portfolio, company, EventType.CYBER_ATTACK, now.minus(Duration.ofDays(1)));

        writer.run(portfolio.getId());

        verify(gemini, times(2)).generate(any(AiRequest.class), any());
        assertThat(recommendations.findByArticleId(first.getId())).isPresent();
        assertThat(recommendations.findByArticleId(second.getId())).isPresent();
    }

    private Company company() {
        String id = UUID.randomUUID().toString().replace("-", "");
        return companyDirectory.findOrCreate("Event Test " + id, String.format("%010d", Math.abs(id.hashCode())),
                null, List.of());
    }


    private NewsArticle alerted(Portfolio portfolio, Company company, EventType type, Instant publishedAt) {
        NewsArticle article = articles.save(new NewsArticle(company.getId(), "https://example.com/" + UUID.randomUUID(),
                "Set by the test: event article", null, publishedAt));
        assessments.save(new NewsAssessment(article.getId(), true, type, Severity.MEDIUM, Confidence.HIGH,
                "Set by the test.", "Set by the test."));
        alerts.save(new Alert(portfolio.getId(), article.getId(), BigDecimal.TEN));
        return article;
    }

    private Event event() {
        Company company = company();
        NewsArticle article = articles.save(new NewsArticle(company.getId(), "https://example.com/" + UUID.randomUUID(),
                "GPUs from stolen $470,000 Nvidia RTX 5090 shipment at Brazil airport appear for sale online, "
                        + "identified by serial numbers — three employees identified by police helped steal equipment",
                null, Instant.now()));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.OTHER_DISRUPTION, Severity.MEDIUM,
                Confidence.HIGH, "A $470,000 shipment of Nvidia RTX 5090 GPUs was stolen from an airport in Brazil "
                + "with the help of employees.", "The headline reports the theft of a specific shipment of graphics "
                + "cards, directly disrupting transit/delivery of sold goods."));
        return new Event(article);
    }

    private Portfolio portfolio() {
        String email = "recommendation-" + UUID.randomUUID() + "@example.com";
        return portfolios.save(new Portfolio(logins.user(email).getId(), "Recommendation test"));
    }

    private record Event(NewsArticle article) {}
}
