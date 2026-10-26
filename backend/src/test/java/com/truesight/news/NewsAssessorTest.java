package com.truesight.news;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiRequest;
import com.truesight.relationship.GeminiClient;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsAssessorTest {

    @Test
    void aSingleBatchWorkerJudgesEachBatchOneAtATime() {
        NewsCheckService newsCheck = mock(NewsCheckService.class);
        NewsArticleRepository articles = mock(NewsArticleRepository.class);
        NewsAssessmentRepository assessments = mock(NewsAssessmentRepository.class);
        GeminiClient gemini = mock(GeminiClient.class);
        AiCallLog calls = mock(AiCallLog.class);

        List<NewsArticle> sample = new GoogleNewsParser().parse(Examples.googleNewsTsmc()).stream()
                .map(item -> new NewsArticle(1L, item.url(), item.title(), item.source(), item.publishedAt()))
                .toList();
        when(newsCheck.companiesFor(1L))
                .thenReturn(List.of(new NewsCheckService.NewsCompany(1L, "Taiwan Semiconductor Manufacturing")));
        when(articles.findUnassessedByCompanyIdIn(anyCollection())).thenReturn(sample);

        AtomicInteger running = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(gemini.generate(any(AiRequest.class), any())).thenAnswer(call -> {
            maximum.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(40);
                return new AiAnswer(Examples.geminiNewsAssessment(), null);
            } finally {
                running.decrementAndGet();
            }
        });

        ExecutorService oneWorker = Executors.newSingleThreadExecutor();
        try {
            NewsAssessor assessor = new NewsAssessor(newsCheck, articles, assessments, gemini, calls, oneWorker);
            assessor.run(1L);

            assertThat(sample.size()).isGreaterThan(20);
            assertThat(maximum.get()).isEqualTo(1);
            verify(gemini, times((sample.size() + NewsAssessor.BATCH_SIZE - 1) / NewsAssessor.BATCH_SIZE))
                    .generate(any(AiRequest.class), any());
        } finally {
            oneWorker.shutdownNow();
        }
    }
}
