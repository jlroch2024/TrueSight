package com.truesight.news;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.ReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Writes one analyst-reviewed research suggestion for each newly alerted article. */
@Component
@Order(3)
public class RecommendationWriter implements NewsCheckStep {
    private static final Logger log = LoggerFactory.getLogger(RecommendationWriter.class);
    static final String PROMPT = """
            Give a portfolio manager one practical research step to check or watch based only on the event facts below.
            Write one or two concise sentences. This is research support, never investment advice: do not recommend
            buying, selling or holding securities, give a price target, or suggest changing portfolio allocation.
            Do not use any of these terms as whole words: buy, sell, hold, price target, overweight, underweight.
            Answer JSON only as {"nextStep":"..."}.
            """;
    static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT", "properties",
            Map.of("nextStep", Map.of("type", "STRING")), "required", List.of("nextStep"),
            "propertyOrdering", List.of("nextStep"));
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(buy|sell|hold|price\\s+target|overweight|underweight)\\b",
            Pattern.CASE_INSENSITIVE);

    private final NewsArticleRepository articles;
    private final NewsAssessmentRepository assessments;
    private final AlertRepository alerts;
    private final RecommendationRepository recommendations;
    private final CompanyRepository companies;
    private final RelationshipRepository relationships;
    private final GeminiClient gemini;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Long, Object> articleLocks = new ConcurrentHashMap<>();

    public RecommendationWriter(NewsArticleRepository articles, NewsAssessmentRepository assessments,
                               AlertRepository alerts, RecommendationRepository recommendations,
                               CompanyRepository companies, RelationshipRepository relationships,
                               GeminiClient gemini, ObjectMapper mapper) {
        this.articles = articles;
        this.assessments = assessments;
        this.alerts = alerts;
        this.recommendations = recommendations;
        this.companies = companies;
        this.relationships = relationships;
        this.gemini = gemini;
        this.mapper = mapper;
    }

    @Override
    public void run(Long portfolioId) {
        List<Alert> portfolioAlerts = alerts.findByPortfolioId(portfolioId);
        Set<Long> articleIds = portfolioAlerts.stream().map(Alert::getArticleId).collect(Collectors.toSet());
        if (articleIds.isEmpty()) return;
        Map<Long, NewsAssessment> judgment = assessments.findByArticleIdIn(articleIds).stream()
                .collect(Collectors.toMap(NewsAssessment::getArticleId, item -> item));
        for (NewsArticle article : articles.findAllById(articleIds)) {
            if (!judgment.containsKey(article.getId())) continue;
            synchronized (articleLocks.computeIfAbsent(article.getId(), ignored -> new Object())) {
                if (recommendations.findByArticleId(article.getId()).isPresent()) continue;
                try {
                    recommendations.save(write(article, judgment.get(article.getId())));
                } catch (RuntimeException failure) {
                    // Recommendation generation must never fail the news check; absence makes this article eligible next time.
                    log.warn("Could not write a recommendation for article {}. It will be retried on the next news check.",
                            article.getId(), failure);
                }
            }
        }
    }

    private Recommendation write(NewsArticle article, NewsAssessment assessment) {
        Company company = companies.findById(article.getCompanyId()).orElse(null);
        String companyName = company == null ? "the company" : company.getName();
        List<Relationship> links = relationships.findByCompanyIdInOrCounterpartyIdIn(
                        Set.of(article.getCompanyId()), Set.of(article.getCompanyId())).stream()
                .filter(link -> link.getReviewStatus() != ReviewStatus.REJECTED).toList();
        Set<Long> relatedIds = links.stream().map(link -> link.getCompanyId().equals(article.getCompanyId())
                ? link.getCounterpartyId() : link.getCompanyId()).collect(Collectors.toSet());
        String related = companies.findAllById(relatedIds).stream().map(Company::getName).distinct().limit(30)
                .collect(Collectors.joining(", "));
        String prompt = PROMPT + "\nCompany: " + companyName + "\nHeadline: " + article.getTitle()
                + "\nEvent summary: " + assessment.getSummary() + "\nReason: " + assessment.getReason()
                + "\nRecorded suppliers and customers: " + (related.isBlank() ? "none" : related);
        AiAnswer answer = gemini.generate(AiRequest.of(AiPurpose.RECOMMEND, prompt), SCHEMA);
        String text = read(answer.text());
        Recommendation recommendation = new Recommendation(article.getId(), text);
        if (isInvestmentAdvice(text)) {
            recommendation.setStatus(RecommendationStatus.WITHHELD);
            recommendation.setNote("Read as investment advice.");
        }
        return recommendation;
    }

    static boolean isInvestmentAdvice(String text) {
        return FORBIDDEN.matcher(text).find();
    }

    String read(String json) {
        try {
            String text = mapper.readTree(answerText(json)).path("nextStep").asString("").strip();
            if (text.isBlank() || text.length() > 1000) throw new IllegalArgumentException("Invalid nextStep");
            return text;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The AI's recommendation could not be read.", exception);
        }
    }

    private String answerText(String response) {
        JsonNode parts = mapper.readTree(response).path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asString(""));
        }
        if (text.isEmpty()) throw new IllegalArgumentException("The AI's recommendation could not be read.");
        return text.toString();
    }
}
