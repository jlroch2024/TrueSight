package com.truesight.news;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.relationship.GeminiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Filter Out Noise: the first news-check step. It asks Gemini whether each new headline about this portfolio's
 * companies reports a real supply-chain event, or is ordinary news, and saves one {@link NewsAssessment} per article.
 *
 * <p>Like relationships, a judgement belongs to the article, not to a portfolio, so it is made once and shared: an
 * article already judged is never sent again. Headlines go in batches of up to {@link #BATCH_SIZE}, numbered from 1,
 * each with the company it was found for. The AI sees only the headline.
 *
 * <p>Two checks never wait for each other to <em>send</em> headlines: each article has its own claim, an article
 * another check is already judging is left to it, and an article is read again once claimed, so it is never judged
 * twice. After sending its own, a check waits for the articles it left to the other, so its alerts include them.
 *
 * <p>An answer that cannot be read is asked for once more. If Gemini then still fails, this throws
 * {@link GeminiClient.GeminiException}; the articles not yet judged stay that way, and the next news check sends them
 * again. {@link NewsCheckJob} shows {@link #AI_FAILED} on the portfolio.
 */
@Component
@Order(1)
public class NewsAssessor implements NewsCheckStep {

    /** The most headlines sent to Gemini at once. */
    static final int BATCH_SIZE = 20;

    /** What the portfolio shows when Gemini fails. */
    public static final String AI_FAILED = "The AI could not assess the news. Please try again later.";

    static final String PROMPT = """
            You are screening news headlines for supply-chain risk. For each numbered headline, decide whether it reports an event
            that could stop or delay the named company making, buying, shipping or selling goods. If it could not do that, it is
            noise, however serious or newsworthy it is.

            These are noise unless the headline itself says production, supply or sales are affected:
            - a leadership or board change
            - a lawsuit, settlement, patent fight or other dispute about money or intellectual property
            - one consumer's accident, or an investigation into a single product incident
            - a regulator's comment, opinion or routine review
            - earnings, share prices, analyst opinions, product launches, marketing, or general commentary

            Answer for every headline:
            - index: the headline's number
            - isEvent: true only for a real disruption; false for noise
            - eventType: one of PRODUCTION_STOP, SUPPLY_SHORTAGE, LABOUR_ACTION, NATURAL_DISASTER, TRADE_RESTRICTION,
              FINANCIAL_DISTRESS, LEGAL_OR_REGULATORY, CYBER_ATTACK, OTHER_DISRUPTION; empty when isEvent is false
            - severity: LOW, MEDIUM or HIGH; empty when isEvent is false
            - confidence: LOW, MEDIUM or HIGH, how sure you are from the headline alone
            - summary: one sentence saying what happened; empty when isEvent is false
            - reason: one sentence explaining your decision

            Use only the headline. Do not guess facts it does not state. Answer with JSON only.

            HEADLINES:
            """;

    private static final List<String> FIELDS =
            List.of("index", "isEvent", "eventType", "severity", "confidence", "summary", "reason");

    /** An object whose {@code assessments} is a list of one judgement per headline, matched to it by index. */
    public static final Map<String, Object> NEWS_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("assessments", Map.of(
                    "type", "ARRAY",
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "index", Map.of("type", "INTEGER"),
                                    "isEvent", Map.of("type", "BOOLEAN"),
                                    "eventType", Map.of("type", "STRING"),
                                    "severity", Map.of("type", "STRING"),
                                    "confidence", Map.of("type", "STRING"),
                                    "summary", Map.of("type", "STRING"),
                                    "reason", Map.of("type", "STRING")),
                            "required", FIELDS,
                            "propertyOrdering", FIELDS))),
            "required", List.of("assessments"));

    private final NewsCheckService newsCheck;
    private final NewsArticleRepository articles;
    private final NewsAssessmentRepository assessments;
    private final GeminiClient gemini;
    private final AiCallLog calls;
    private final Executor batchExecutor;
    private final NewsAssessmentReader reader = new NewsAssessmentReader();

    // One claim per article being judged, so two portfolios sharing a company never judge the same article twice,
    // yet never wait for each other's other articles. Completed when the article's judging has ended.
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> articleLocks = new ConcurrentHashMap<>();

    public NewsAssessor(NewsCheckService newsCheck, NewsArticleRepository articles,
                        NewsAssessmentRepository assessments, GeminiClient gemini, AiCallLog calls,
                        @Qualifier("newsBatchExecutor") Executor batchExecutor) {
        this.newsCheck = newsCheck;
        this.articles = articles;
        this.assessments = assessments;
        this.gemini = gemini;
        this.calls = calls;
        this.batchExecutor = batchExecutor;
    }

    @Override
    public void run(Long portfolioId) {
        Map<Long, String> names = newsCheck.companiesFor(portfolioId).stream()
                .collect(Collectors.toMap(NewsCheckService.NewsCompany::id, NewsCheckService.NewsCompany::name,
                        (first, second) -> first));
        if (names.isEmpty()) return;

        // Claim the unjudged articles. One that another check has claimed is skipped: it is being judged already.
        Map<Long, CompletableFuture<Void>> mine = new LinkedHashMap<>();
        Map<Long, NewsArticle> claimed = new LinkedHashMap<>();
        List<CompletableFuture<Void>> others = new ArrayList<>();
        try {
            for (NewsArticle article : articles.findUnassessedByCompanyIdIn(names.keySet())) {
                CompletableFuture<Void> claim = new CompletableFuture<>();
                CompletableFuture<Void> existing = articleLocks.putIfAbsent(article.getId(), claim);
                if (existing == null) {
                    mine.put(article.getId(), claim);
                    claimed.put(article.getId(), article);
                } else {
                    others.add(existing);
                }
            }
            // Read again now the articles are ours: another check may have judged some since the first read.
            if (!claimed.isEmpty()) {
                assessments.findByArticleIdIn(List.copyOf(claimed.keySet())).forEach(done -> {
                    claimed.remove(done.getArticleId());
                    release(mine, done.getArticleId());
                });
            }

            List<NewsArticle> waiting = List.copyOf(claimed.values());
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int start = 0; start < waiting.size(); start += BATCH_SIZE) {
                List<NewsArticle> batch = List.copyOf(waiting.subList(start, Math.min(start + BATCH_SIZE, waiting.size())));
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        save(batch, ask(prompt(batch, names), batch.size()));
                    } finally {
                        batch.forEach(article -> release(mine, article.getId()));
                    }
                }, batchExecutor));
            }
            try {
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof RuntimeException cause) throw cause;
                throw exception;
            }
        } finally {
            // Whatever happened, nothing stays claimed: an article not judged is simply judged by the next check.
            new ArrayList<>(mine.keySet()).forEach(id -> release(mine, id));
        }
        // The articles another check was judging are judged once that check has finished, so the alerts built next
        // see them. Nothing here waits before this check's own headlines have been sent.
        others.forEach(CompletableFuture::join);
    }

    /** Ends this check's claim on an article. Safe to call twice. */
    private void release(Map<Long, CompletableFuture<Void>> mine, Long articleId) {
        CompletableFuture<Void> claim = mine.get(articleId);
        if (claim != null) {
            articleLocks.remove(articleId, claim);
            claim.complete(null);
        }
    }

    /** The prompt for one batch: each headline numbered from 1, after the name of the company it was found for. */
    static String prompt(List<NewsArticle> batch, Map<Long, String> companyNames) {
        StringBuilder prompt = new StringBuilder(PROMPT);
        for (int i = 0; i < batch.size(); i++) {
            NewsArticle article = batch.get(i);
            String company = NewsSearchName.of(companyNames.getOrDefault(article.getCompanyId(), ""));
            prompt.append(i + 1).append(". [").append(company).append("] ").append(article.getTitle()).append('\n');
        }
        return prompt.toString();
    }

    /** Asks Gemini, and asks once more if the answer cannot be read. Each unreadable answer is recorded as such. */
    private Map<Integer, NewsAssessmentReader.Judgement> ask(String prompt, int headlineCount) {
        AiRequest request = AiRequest.of(AiPurpose.JUDGE_NEWS, prompt); // headlines only: no report text to hide
        try {
            return read(request, headlineCount);
        } catch (NewsAssessmentReader.UnreadableAnswerException first) {
            try {
                return read(request, headlineCount);
            } catch (NewsAssessmentReader.UnreadableAnswerException second) {
                throw new GeminiClient.GeminiException(
                        "The AI's answer could not be read, even when asked a second time.", second);
            }
        }
    }

    private Map<Integer, NewsAssessmentReader.Judgement> read(AiRequest request, int headlineCount) {
        AiAnswer answer = gemini.generate(request, NEWS_SCHEMA);
        try {
            return reader.read(answer.text(), headlineCount);
        } catch (NewsAssessmentReader.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId());
            throw exception;
        }
    }

    /** Saves one assessment per answered headline. A headline with no answer stays unassessed, for next time. */
    private void save(List<NewsArticle> batch, Map<Integer, NewsAssessmentReader.Judgement> judgements) {
        List<NewsAssessment> answered = new ArrayList<>();
        judgements.forEach((number, judgement) -> {
            NewsArticle article = batch.get(number - 1); // the reader only keeps numbers 1 to batch.size()
            if (!assessments.existsByArticleId(article.getId())) {
                answered.add(new NewsAssessment(article.getId(), judgement.event(), judgement.eventType(),
                        judgement.severity(), judgement.confidence(), judgement.summary(), judgement.reason()));
            }
        });
        assessments.saveAll(answered);
    }
}
