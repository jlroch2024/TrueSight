package com.truesight.news;

import com.truesight.relationship.GeminiClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects news away from the request thread, one shared company at a time, in three rounds: the holdings' own news,
 * then their direct suppliers', then the suppliers' suppliers'. After each round the headlines are judged and the
 * alerts built, so the most important alerts exist first.
 */
@Component
public class NewsCheckJob {

    /** A holding or a direct supplier is asked again after an hour. */
    static final Duration WINDOW = Duration.ofHours(1);
    /** A company that is only a supplier's supplier in the portfolio is not asked again for six hours. */
    static final Duration SUPPLIERS_SUPPLIERS_WINDOW = Duration.ofHours(6);
    private static final int LAST_ROUND = 2;

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
        check(portfolioId);
    }

    /** The daily check: every portfolio with an analysed holding, one after another. */
    @Async
    public void runDaily() {
        for (Long portfolioId : service.portfolioIdsWithAnalysedHoldings()) {
            if (service.startForDaily(portfolioId)) {
                check(portfolioId);
            }
        }
    }

    private void check(Long portfolioId) {
        try {
            NewsCheckService.NewsRounds rounds = service.roundsFor(portfolioId);
            List<List<NewsCheckService.NewsCompany>> inOrder =
                    List.of(rounds.holdings(), rounds.suppliers(), rounds.suppliersSuppliers());
            int attempted = 0;
            int succeeded = 0;
            List<String> failed = new ArrayList<>();
            for (int round = 0; round < inOrder.size(); round++) {
                Duration window = round == LAST_ROUND ? SUPPLIERS_SUPPLIERS_WINDOW : WINDOW;
                Fetched fetched = fetch(inOrder.get(round), window, failed);
                attempted += fetched.attempted();
                succeeded += fetched.succeeded();
                // Judge and alert now, so this round's alerts exist before the next round's news is fetched. A round
                // that fetched nothing new has nothing to add, except the first: it also judges what earlier checks
                // left unjudged. A round that failed to fetch every company does not stop the next one.
                boolean roundFailedEntirely = fetched.attempted() > 0 && fetched.succeeded() == 0;
                if (!roundFailedEntirely && (round == 0 || fetched.succeeded() > 0)) {
                    steps.run(portfolioId);
                }
            }
            if (attempted > 0 && succeeded == 0) {
                service.fail(portfolioId, NewsClient.UNREACHABLE);
                return;
            }
            service.finish(portfolioId, Instant.now(), failed.isEmpty() ? null : missedReason(failed, attempted));
        } catch (GeminiClient.GeminiException exception) {
            // The articles the AI did not judge stay unassessed, so the next Check News sends them again.
            service.fail(portfolioId, NewsAssessor.AI_FAILED);
        } catch (RuntimeException exception) {
            service.fail(portfolioId, "News could not be checked. Please try again later.");
        }
    }

    /** Fetches and saves one round, one company at a time. A company checked within {@code window} is skipped. */
    private Fetched fetch(List<NewsCheckService.NewsCompany> round, Duration window, List<String> failed) {
        int attempted = 0;
        int succeeded = 0;
        for (NewsCheckService.NewsCompany company : round) {
            synchronized (companyLocks.computeIfAbsent(company.id(), ignored -> new Object())) {
                Instant now = Instant.now();
                if (service.wasCheckedWithin(company.id(), now, window)) continue;
                attempted++;
                String searchName = NewsSearchName.of(company.name());
                try {
                    List<NewsItem> recent = newestRecentItems(news.search(searchName), now);
                    service.save(company.id(), recent, now);
                    succeeded++;
                } catch (NewsClient.NewsAccessException ignored) {
                    // A failed company stays unchecked so this portfolio, or another one, can try it again.
                    failed.add(searchName);
                }
            }
        }
        return new Fetched(attempted, succeeded);
    }

    private record Fetched(int attempted, int succeeded) {
    }

    private static String missedReason(List<String> failed, int attempted) {
        int shown = Math.min(5, failed.size());
        String names = String.join(", ", failed.subList(0, shown));
        if (failed.size() > shown) {
            names += " and " + (failed.size() - shown) + " more";
        }
        String prefix = "News for %d of %d companies could not be fetched: ".formatted(failed.size(), attempted);
        String suffix = ". Check News again to retry " + (failed.size() == 1 ? "it." : "them.");
        int available = 500 - prefix.length() - suffix.length();
        if (names.length() > available) {
            names = names.substring(0, available - 1) + "…";
        }
        return prefix + names + suffix;
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
