package com.truesight.news;

import com.truesight.relationship.GeminiClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Collects news away from the request thread, one shared company at a time. */
@Component
public class NewsCheckJob {

    private final NewsCheckService service;
    private final NewsClient news;
    private final NewsCheckStepRunner steps;
    private final ConcurrentHashMap<Long, Object> companyLocks = new ConcurrentHashMap<>();

    public NewsCheckJob(NewsCheckService service, NewsClient news, NewsCheckStepRunner steps) {
        this.service = service;
        this.news = news;
        this.steps = steps;
    }

    @Async
    public void run(Long portfolioId) {
        try {
            int attempted = 0;
            int succeeded = 0;
            for (NewsCheckService.NewsCompany company : service.companiesFor(portfolioId)) {
                synchronized (companyLocks.computeIfAbsent(company.id(), ignored -> new Object())) {
                    Instant now = Instant.now();
                    if (service.wasCheckedWithinTheLastHour(company.id(), now)) continue;
                    attempted++;
                    try {
                        List<NewsItem> recent = newestRecentItems(news.search(NewsSearchName.of(company.name())), now);
                        service.save(company.id(), recent, now);
                        succeeded++;
                    } catch (NewsClient.NewsAccessException ignored) {
                        // A failed company stays unchecked so this portfolio, or another one, can try it again.
                    }
                }
            }
            if (attempted > 0 && succeeded == 0) {
                service.fail(portfolioId, NewsClient.UNREACHABLE);
                return;
            }
            steps.run(portfolioId);
            service.finish(portfolioId, Instant.now());
        } catch (GeminiClient.GeminiException exception) {
            // The articles the AI did not judge stay unassessed, so the next Check News sends them again.
            service.fail(portfolioId, NewsAssessor.AI_FAILED);
        } catch (RuntimeException exception) {
            service.fail(portfolioId, "News could not be checked. Please try again later.");
        }
    }

    private List<NewsItem> newestRecentItems(List<NewsItem> items, Instant now) {
        Instant oldest = now.minus(Duration.ofDays(7));
        Set<String> urls = new HashSet<>();
        return items.stream()
                .filter(item -> !item.publishedAt().isBefore(oldest) && !item.publishedAt().isAfter(now))
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
                .filter(item -> urls.add(item.url()))
                .limit(10)
                .toList();
    }
}
