package com.truesight.news;

import com.truesight.relationship.GeminiClient;
import com.truesight.report.SecClient;
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
 * Collects news away from the request thread, one shared company at a time: its Google News headlines and, for a
 * company with an SEC number, its 8-Ks (see {@link CurrentReports}).
 */
@Component
public class NewsCheckJob {

    private final NewsCheckService service;
    private final NewsClient news;
    private final SecClient sec;
    private final NewsCheckStepRunner steps;
    private final ConcurrentHashMap<Long, Object> companyLocks = new ConcurrentHashMap<>();

    public NewsCheckJob(NewsCheckService service, NewsClient news, SecClient sec, NewsCheckStepRunner steps) {
        this.service = service;
        this.news = news;
        this.sec = sec;
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
            int attempted = 0;
            int succeeded = 0;
            List<String> failed = new ArrayList<>();
            for (NewsCheckService.NewsCompany company : service.companiesFor(portfolioId)) {
                synchronized (companyLocks.computeIfAbsent(company.id(), ignored -> new Object())) {
                    Instant now = Instant.now();
                    if (service.wasCheckedWithinTheLastHour(company.id(), now)) continue;
                    attempted++;
                    String searchName = NewsSearchName.of(company.name());
                    try {
                        List<NewsItem> found = new ArrayList<>(news.search(searchName));
                        found.addAll(currentReports(company, now));
                        List<NewsItem> recent = newestRecentItems(found, now);
                        service.save(company.id(), recent, now);
                        succeeded++;
                    } catch (NewsClient.NewsAccessException ignored) {
                        // A failed company stays unchecked so this portfolio, or another one, can try it again.
                        failed.add(searchName);
                    }
                }
            }
            if (attempted > 0 && succeeded == 0) {
                service.fail(portfolioId, NewsClient.UNREACHABLE);
                return;
            }
            steps.run(portfolioId);
            service.finish(portfolioId, Instant.now(), failed.isEmpty() ? null : missedReason(failed, attempted));
        } catch (GeminiClient.GeminiException exception) {
            // The articles the AI did not judge stay unassessed, so the next Check News sends them again.
            service.fail(portfolioId, NewsAssessor.AI_FAILED);
        } catch (RuntimeException exception) {
            service.fail(portfolioId, "News could not be checked. Please try again later.");
        }
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

    /**
     * A company's 8-Ks that report one of the nine items, as articles: by the same rules as news (the last 7 days,
     * newest first, at most 10), and only those not already saved, so none is downloaded twice. None for a company
     * without an SEC number, or when the SEC cannot be reached: its Google News is still saved, and its 8-Ks are read
     * on its next check.
     */
    private List<NewsItem> currentReports(NewsCheckService.NewsCompany company, Instant now) {
        if (company.cik() == null || company.cik().isBlank()) return List.of();
        List<SecClient.SecCurrentReport> filings;
        try {
            filings = sec.recentCurrentReports(company.cik());
        } catch (SecClient.SecAccessException exception) {
            return List.of();
        }
        Instant oldest = now.minus(Duration.ofDays(7));
        return CurrentReports.kept(filings).stream()
                .filter(filing -> !filing.acceptedAt().isBefore(oldest) && !filing.acceptedAt().isAfter(now))
                .sorted(Comparator.comparing(SecClient.SecCurrentReport::acceptedAt).reversed())
                .limit(10)
                .filter(filing -> !service.isSaved(company.id(), filing.url()))
                .map(filing -> CurrentReports.newsItem(company.name(), filing, download(filing.url())))
                .toList();
    }

    /** The filing's document, or null when it cannot be downloaded: its headline is then the item's name. */
    private String download(String url) {
        try {
            return sec.download(url);
        } catch (SecClient.SecAccessException exception) {
            return null;
        }
    }
}
