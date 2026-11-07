package com.truesight.news;

import com.truesight.relationship.GeminiClient;
import com.truesight.report.SecClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects news away from the request thread, in three rounds, one shared company at a time: each company's Google News
 * headlines and, for a company with an SEC number, its 8-Ks (see {@link CurrentReports}).
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

    /** How long a holding's or a direct supplier's news is kept before it is fetched again. */
    static final Duration FRESH = Duration.ofHours(1);

    /** The same for a company that is only a supplier's supplier in the portfolio: most of the companies. */
    static final Duration FRESH_FOR_SUPPLIERS_SUPPLIERS = Duration.ofHours(6);

    /**
     * Fetches the news in three rounds, most important first: the holdings', their suppliers', then the suppliers'
     * suppliers'. After each round the headlines are judged and the alerts built, so an alert about a holding exists
     * within seconds, and the suppliers' follow. A round that fetches nothing new does not stop the next.
     */
    private void check(Long portfolioId) {
        try {
            NewsCheckService.NewsRounds rounds = service.roundsFor(portfolioId);
            Fetched total = new Fetched();
            round(portfolioId, rounds.holdings(), FRESH, total);
            round(portfolioId, rounds.suppliers(), FRESH, total);
            round(portfolioId, rounds.suppliersSuppliers(), FRESH_FOR_SUPPLIERS_SUPPLIERS, total);
            if (total.attempted > 0 && total.succeeded == 0) {
                service.fail(portfolioId, NewsClient.UNREACHABLE);
                return;
            }
            service.finish(portfolioId, Instant.now(), total.failed.isEmpty() ? null : missedReason(total.failed, total.attempted));
        } catch (GeminiClient.GeminiException exception) {
            // The AI is not answering, so the later rounds could not be judged either: the check stops here and fails.
            // What the earlier rounds fetched is kept, and the articles the AI did not judge stay unassessed, so the
            // next Check News judges them (and fetches the rounds it did not reach).
            service.fail(portfolioId, NewsAssessor.AI_FAILED);
        } catch (RuntimeException exception) {
            service.fail(portfolioId, "News could not be checked. Please try again later.");
        }
    }

    /** What the rounds so far have fetched. */
    private static final class Fetched {
        int attempted;
        int succeeded;
        final List<String> failed = new ArrayList<>();
    }

    /**
     * Fetches one round's companies, unless they were fetched within {@code fresh}, then has the steps judge the
     * headlines and build the alerts. A round whose every fetch failed has nothing new for the steps.
     */
    private void round(Long portfolioId, List<NewsCheckService.NewsCompany> companies, Duration fresh, Fetched total) {
        if (companies.isEmpty()) return;
        int attempted = 0;
        int succeeded = 0;
        for (NewsCheckService.NewsCompany company : companies) {
            synchronized (companyLocks.computeIfAbsent(company.id(), ignored -> new Object())) {
                Instant now = Instant.now();
                if (service.wasCheckedWithin(company.id(), now, fresh)) continue;
                attempted++;
                String searchName = NewsSearchName.of(company.name());
                try {
                    service.save(company.id(), itemsFor(company, searchName, now), now);
                    succeeded++;
                } catch (NewsClient.NewsAccessException ignored) {
                    // A failed company stays unchecked so this portfolio, or another one, can try it again.
                    total.failed.add(searchName);
                }
            }
        }
        total.attempted += attempted;
        total.succeeded += succeeded;
        if (attempted > 0 && succeeded == 0) return;
        steps.run(portfolioId);
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

    /**
     * What one company's check saves: its Google News headlines and its 8-Ks together, under the one rule for both (the
     * last 7 days, newest first, at most 10). Only then are the 8-Ks that remain downloaded, so one that the rule leaves
     * out is never downloaded for nothing, and one already saved is never downloaded again.
     */
    private List<NewsItem> itemsFor(NewsCheckService.NewsCompany company, String searchName, Instant now) {
        List<NewsItem> found = new ArrayList<>(news.search(searchName));
        Map<String, SecClient.SecCurrentReport> filings = new HashMap<>();
        for (SecClient.SecCurrentReport filing : currentReports(company)) {
            filings.put(filing.url(), filing);
            // Until it is downloaded, an 8-K is only the item's name: enough to place it among the news.
            found.add(CurrentReports.newsItem(company.name(), filing, null));
        }
        return newestRecentItems(found, now).stream()
                .map(item -> {
                    SecClient.SecCurrentReport filing = filings.get(item.url());
                    return filing == null || service.isSaved(company.id(), filing.url())
                            ? item
                            : CurrentReports.newsItem(company.name(), filing, download(filing.url()));
                })
                .toList();
    }

    /**
     * A company's 8-Ks that report one of the nine items. None for a company without an SEC number, or when the SEC
     * cannot be reached: its Google News is still saved, and its 8-Ks are read on its next check.
     */
    private List<SecClient.SecCurrentReport> currentReports(NewsCheckService.NewsCompany company) {
        if (!company.hasSecNumber()) return List.of();
        try {
            return CurrentReports.kept(sec.recentCurrentReports(company.cik()));
        } catch (SecClient.SecAccessException exception) {
            return List.of();
        }
    }

    /** The filing's document, or null when it cannot be downloaded: its headline is then the item's name. */
    private String download(String url) {
        try {
            return sec.download(url);
        } catch (SecClient.SecAccessException exception) {
            return null;
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
