package com.truesight.news;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What counts as one event: articles about the same company, with the same event type, published within three days
 * of the event's first article. Alert cards and suggestions both ask here, so they can never disagree.
 */
public final class NewsEvents {

    static final Duration EVENT_WINDOW = Duration.ofDays(3);

    private NewsEvents() {
    }

    /** Groups chronologically, anchoring each three-day window at its earliest article. */
    public static <T> List<List<T>> group(List<T> items, Function<T, NewsArticle> article,
                                          Function<T, NewsAssessment> assessment) {
        Map<GroupKey, List<T>> byKey = new HashMap<>();
        for (T item : items) {
            GroupKey key = new GroupKey(article.apply(item).getCompanyId(), assessment.apply(item).getEventType());
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<List<T>> events = new ArrayList<>();
        for (List<T> candidates : byKey.values()) {
            List<T> chronological = candidates.stream()
                    .sorted(Comparator.comparing(item -> article.apply(item).getPublishedAt()))
                    .toList();
            List<T> current = new ArrayList<>();
            for (T item : chronological) {
                if (!current.isEmpty() && Duration.between(article.apply(current.getFirst()).getPublishedAt(),
                        article.apply(item).getPublishedAt()).compareTo(EVENT_WINDOW) > 0) {
                    events.add(List.copyOf(current));
                    current.clear();
                }
                current.add(item);
            }
            if (!current.isEmpty()) events.add(List.copyOf(current));
        }
        return events;
    }

    /**
     * The ids of every article in the event that {@code articleId} belongs to, given all saved articles of its
     * company and their assessments. Only articles judged to be events of the same type can share an event.
     */
    static List<Long> articleIdsOfEventContaining(Long articleId, List<NewsArticle> companyArticles,
                                                  List<NewsAssessment> assessments) {
        Map<Long, NewsAssessment> byArticle = new HashMap<>();
        assessments.stream().filter(found -> found.isEvent() && found.getEventType() != null)
                .forEach(found -> byArticle.put(found.getArticleId(), found));
        List<NewsArticle> judged = companyArticles.stream().filter(found -> byArticle.containsKey(found.getId())).toList();
        return group(judged, Function.identity(), found -> byArticle.get(found.getId())).stream()
                .filter(event -> event.stream().anyMatch(found -> found.getId().equals(articleId)))
                .findFirst().map(event -> event.stream().map(NewsArticle::getId).toList())
                .orElse(List.of(articleId));
    }

    private record GroupKey(Long companyId, EventType eventType) {
    }
}
