package com.truesight.news;

import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * See What a News Event Affects: the second news-check step. For each article the AI judged a real event, it finds
 * the portfolio's holdings the event reaches, and saves one {@link Alert} listing them.
 *
 * <p>An event about company E reaches holding H when H is E itself, or when a relationship that is not Rejected joins
 * them one step along the graph, whichever report it came from and whichever way it points: E supplies H, E buys from
 * H, H supplies E, or H buys from E. If several relationships join the same pair, the Approved one is kept, then the
 * lowest id.
 *
 * <p>The alert's exposure is the total weight of the holdings reached (12.5 means 12.5%). A holding with no weight
 * counts as 0; if no holding in the portfolio has a weight, exposure is the number of holdings reached instead.
 *
 * <p>Noise never becomes an alert, and an article that already has an alert in this portfolio is skipped, so checking
 * the news again never repeats one.
 *
 * <p>When an analyst changes an article's judgement, {@link #rebuildFor} brings every portfolio's alerts for it up to
 * date at once.
 */
@Component
@Order(2)
public class AlertBuilder implements NewsCheckStep {

    /** Approved first, then the lowest id: the relationship kept when several join the same holding and company. */
    private static final Comparator<Relationship> PREFERRED = Comparator
            .comparing((Relationship relationship) -> relationship.getReviewStatus() != ReviewStatus.APPROVED)
            .thenComparing(Relationship::getId);
    private static final Comparator<PathCandidate> PREFERRED_PATH = Comparator
            .comparing(PathCandidate::first, PREFERRED)
            .thenComparing(PathCandidate::second, PREFERRED);

    private final NewsCheckService newsCheck;
    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final NewsArticleRepository articles;
    private final NewsAssessmentRepository assessments;
    private final AlertRepository alerts;
    private final AlertHoldingRepository alertHoldings;
    private final TransactionTemplate transaction;

    public AlertBuilder(NewsCheckService newsCheck, PortfolioRepository portfolios, HoldingRepository holdings,
                        RelationshipRepository relationships, NewsArticleRepository articles,
                        NewsAssessmentRepository assessments, AlertRepository alerts,
                        AlertHoldingRepository alertHoldings, TransactionTemplate transaction) {
        this.newsCheck = newsCheck;
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.articles = articles;
        this.assessments = assessments;
        this.alerts = alerts;
        this.alertHoldings = alertHoldings;
        this.transaction = transaction;
    }

    @Override
    public void run(Long portfolioId) {
        build(portfolioId, null);
    }

    /**
     * Brings every portfolio's alerts for this article in line with its judgement, after an analyst changes it. Noise:
     * the article's alerts go from every portfolio. An event: each portfolio whose news covers the article's company
     * gets its alert, if it has none yet. A new severity needs nothing here, as alerts read it from the assessment.
     */
    public void rebuildFor(Long articleId) {
        NewsArticle article = articles.findById(articleId).orElse(null);
        if (article == null) return;
        boolean event = assessments.findByArticleId(articleId).map(NewsAssessment::isEvent).orElse(false);
        if (!event) {
            transaction.executeWithoutResult(status -> alerts.deleteByArticleId(articleId));
            return;
        }
        for (Portfolio portfolio : portfolios.findAll()) {
            build(portfolio.getId(), article);
        }
    }

    /** Builds the portfolio's missing alerts: for every article its news covers, or only for {@code only}. */
    private void build(Long portfolioId, NewsArticle only) {
        List<Holding> held = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                .filter(holding -> holding.getCompanyId() != null)
                .toList();
        if (held.isEmpty()) return;

        Set<Long> checked = newsCheck.companiesFor(portfolioId).stream()
                .map(NewsCheckService.NewsCompany::id)
                .collect(Collectors.toSet());
        if (checked.isEmpty()) return;

        List<NewsArticle> found = only == null ? articles.findByCompanyIdInOrderByIdAsc(checked)
                : checked.contains(only.getCompanyId()) ? List.of(only) : List.of();
        if (found.isEmpty()) return;
        Set<Long> events = assessments.findByArticleIdIn(found.stream().map(NewsArticle::getId).toList()).stream()
                .filter(NewsAssessment::isEvent)
                .map(NewsAssessment::getArticleId)
                .collect(Collectors.toSet());

        Set<Long> heldCompanies = held.stream().map(Holding::getCompanyId).collect(Collectors.toSet());
        Set<Long> relevantCompanies = Stream.concat(heldCompanies.stream(), checked.stream())
                .collect(Collectors.toSet());
        List<Relationship> links = relationships.findByCompanyIdInOrCounterpartyIdIn(
                        relevantCompanies, relevantCompanies)
                .stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .toList();

        for (NewsArticle article : found) {
            if (!events.contains(article.getId())) continue;
            if (alerts.existsByPortfolioIdAndArticleId(portfolioId, article.getId())) continue;
            Map<Holding, Path> reached = reached(article.getCompanyId(), held, links);
            if (!reached.isEmpty()) {
                save(portfolioId, article.getId(), reached, exposure(reached.keySet(), held));
            }
        }
    }

    /**
     * Each holding an event about {@code eventCompany} reaches, with the relationship that links it, or null when the
     * event is about the holding itself.
     */
    static Map<Holding, Path> reached(Long eventCompany, List<Holding> held, List<Relationship> links) {
        Map<Holding, Path> reached = new LinkedHashMap<>();
        for (Holding holding : held) {
            Long company = holding.getCompanyId();
            if (company.equals(eventCompany)) {
                reached.put(holding, new Path(null, null));
                continue;
            }
            links.stream()
                    .filter(link -> joins(link, company, eventCompany))
                    .min(PREFERRED)
                    .ifPresent(link -> reached.put(holding, new Path(link.getId(), null)));
        }
        for (Holding holding : held) {
            if (reached.containsKey(holding)) continue;
            Long company = holding.getCompanyId();
            links.stream()
                    .filter(second -> supplies(second, supplierOf(second), company))
                    .flatMap(second -> links.stream()
                            .filter(first -> supplies(first, eventCompany, supplierOf(second)))
                            .map(first -> new PathCandidate(first, second)))
                    .min(PREFERRED_PATH)
                    .ifPresent(path -> reached.put(holding,
                            new Path(path.first().getId(), path.second().getId())));
        }
        return reached;
    }

    /** True when the relationship joins these two companies, from either one's report, whichever way it points. */
    private static boolean joins(Relationship link, Long first, Long second) {
        return (link.getCompanyId().equals(first) && link.getCounterpartyId().equals(second))
                || (link.getCompanyId().equals(second) && link.getCounterpartyId().equals(first));
    }

    /** True when this relationship says {@code supplier} supplies {@code customer}. */
    private static boolean supplies(Relationship link, Long supplier, Long customer) {
        return supplierOf(link).equals(supplier) && customerOf(link).equals(customer);
    }

    private static Long supplierOf(Relationship link) {
        return link.getType() == RelationshipType.SUPPLIER ? link.getCounterpartyId() : link.getCompanyId();
    }

    private static Long customerOf(Relationship link) {
        return link.getType() == RelationshipType.SUPPLIER ? link.getCompanyId() : link.getCounterpartyId();
    }

    /** The total weight of the holdings reached, or how many there are when no holding in the portfolio has one. */
    static BigDecimal exposure(Set<Holding> reached, List<Holding> held) {
        if (!weighted(held)) return BigDecimal.valueOf(reached.size());
        return reached.stream()
                .map(Holding::getWeight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** True when at least one holding in the portfolio has a weight, so exposure is a total weight, not a count. */
    static boolean weighted(List<Holding> held) {
        return held.stream().anyMatch(holding -> holding.getWeight() != null);
    }

    /** Saves the alert and its holdings together, so an alert never exists without the holdings it reaches. */
    private void save(Long portfolioId, Long articleId, Map<Holding, Path> reached, BigDecimal exposure) {
        transaction.executeWithoutResult(status -> {
            Alert alert = alerts.save(new Alert(portfolioId, articleId, exposure));
            reached.forEach((holding, path) -> {
                AlertHolding found = new AlertHolding(alert.getId(), holding.getId(), path.relationshipId());
                found.setSecondRelationshipId(path.secondRelationshipId());
                alertHoldings.save(found);
            });
        });
    }

    record Path(Long relationshipId, Long secondRelationshipId) {
    }

    private record PathCandidate(Relationship first, Relationship second) {
    }
}
