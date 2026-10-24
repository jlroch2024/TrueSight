package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Lists and changes the logged-in user's alert events for one portfolio. */
@Service
public class AlertService {

    private static final String PORTFOLIO_NOT_FOUND = "Portfolio not found.";
    private static final String ALERT_NOT_FOUND = "Alert not found.";
    private static final String OPEN_OR_DISMISSED = "An alert is either open or dismissed.";
    private static final Duration EVENT_WINDOW = Duration.ofDays(3);

    private final PortfolioRepository portfolios;
    private final AlertRepository alerts;
    private final AlertHoldingRepository alertHoldings;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final CompanyDisplayNames displayNames;
    private final CurrentUser currentUser;

    public AlertService(PortfolioRepository portfolios, AlertRepository alerts,
                        AlertHoldingRepository alertHoldings, HoldingRepository holdings,
                        RelationshipRepository relationships, CompanyRepository companies,
                        CompanyDisplayNames displayNames, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.alerts = alerts;
        this.alertHoldings = alertHoldings;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.displayNames = displayNames;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public AlertsResponse list(Long portfolioId, boolean includeDismissed) {
        requirePortfolio(portfolioId);
        List<AlertRow> rows = alerts.findOrdered(portfolioId);
        List<Holding> portfolioHoldings = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        PageData data = loadPageData(rows, portfolioHoldings);
        List<AlertEventResponse> allEvents = group(rows).stream()
                .map(event -> eventResponse(event.rows(), data))
                .sorted(EVENT_ORDER)
                .toList();
        List<AlertEventResponse> visible = allEvents.stream()
                .filter(event -> includeDismissed || event.status() != AlertStatus.DISMISSED)
                .toList();
        return new AlertsResponse(AlertBuilder.weighted(portfolioHoldings), portfolioHoldings.size(), visible);
    }

    /** Retained for TS-172's single-alert API. */
    @Transactional
    public AlertResponse setStatus(Long portfolioId, Long alertId, AlertStatus status) {
        rejectAcknowledged(status);
        requirePortfolio(portfolioId);
        Alert alert = alerts.findByIdAndPortfolioId(alertId, portfolioId)
                .orElseThrow(() -> ApiException.notFound(ALERT_NOT_FOUND));
        alert.setStatus(status);
        alerts.save(alert);
        AlertRow row = alerts.findOrdered(portfolioId).stream()
                .filter(found -> found.alert().getId().equals(alertId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(ALERT_NOT_FOUND));
        List<Holding> portfolioHoldings = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        return alertResponse(row, loadPageData(List.of(row), portfolioHoldings));
    }

    /** Changes all alert rows in an event, after confirming that every id belongs to this portfolio. */
    @Transactional
    public AlertEventResponse setEventStatus(Long portfolioId, List<Long> alertIds, AlertStatus status) {
        rejectAcknowledged(status);
        requirePortfolio(portfolioId);
        List<Long> distinctIds = alertIds.stream().distinct().toList();
        List<Alert> found = alerts.findByIdInAndPortfolioId(distinctIds, portfolioId);
        if (found.size() != distinctIds.size()) throw ApiException.notFound(ALERT_NOT_FOUND);

        found.forEach(alert -> alert.setStatus(status));
        alerts.saveAll(found);
        Set<Long> ids = Set.copyOf(distinctIds);
        List<AlertRow> rows = alerts.findOrdered(portfolioId).stream()
                .filter(row -> ids.contains(row.alert().getId()))
                .toList();
        if (rows.size() != distinctIds.size()) throw ApiException.notFound(ALERT_NOT_FOUND);
        List<Holding> portfolioHoldings = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        return eventResponse(rows, loadPageData(rows, portfolioHoldings));
    }

    private PageData loadPageData(List<AlertRow> rows, List<Holding> portfolioHoldings) {
        if (rows.isEmpty()) return PageData.empty(portfolioHoldings);
        List<Long> alertIds = rows.stream().map(row -> row.alert().getId()).toList();
        List<AlertHolding> reached = alertHoldings.findByAlertIdIn(alertIds);
        Map<Long, Holding> holdingById = holdings.findAllById(
                        reached.stream().map(AlertHolding::getHoldingId).distinct().toList())
                .stream().collect(Collectors.toMap(Holding::getId, Function.identity()));
        Map<Long, Relationship> relationshipById = relationships.findAllById(
                        reached.stream().map(AlertHolding::getRelationshipId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Relationship::getId, Function.identity()));
        Set<Long> companyIds = relationshipById.values().stream()
                .flatMap(relationship -> Stream.of(relationship.getCompanyId(), relationship.getCounterpartyId()))
                .collect(Collectors.toSet());
        Map<Long, Company> companyById = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, List<AlertHolding>> reachedByAlert = reached.stream()
                .collect(Collectors.groupingBy(AlertHolding::getAlertId));
        return new PageData(portfolioHoldings, holdingById, relationshipById, companyById, reachedByAlert);
    }

    private AlertEventResponse eventResponse(List<AlertRow> rows, PageData data) {
        List<AlertRow> ordered = rows.stream()
                .sorted(Comparator.comparing((AlertRow row) -> row.article().getPublishedAt()).reversed())
                .toList();
        AlertRow lead = lead(rows);
        List<Long> alertIds = Stream.concat(Stream.of(lead), ordered.stream().filter(row -> row != lead))
                .map(row -> row.alert().getId())
                .toList();
        Map<HoldingRelationshipKey, AlertEventResponse.ReachedHolding> reachedByPair = new LinkedHashMap<>();
        ordered.stream()
                .flatMap(row -> data.reachedByAlert().getOrDefault(row.alert().getId(), List.of()).stream())
                .map(found -> eventReachedHolding(found, data))
                .filter(Objects::nonNull)
                .forEach(reached -> reachedByPair.putIfAbsent(
                        new HoldingRelationshipKey(reached.holdingId(), reached.relationshipId()), reached));
        List<AlertEventResponse.ReachedHolding> reached = reachedByPair.values().stream()
                .sorted(Comparator.comparing(AlertEventResponse.ReachedHolding::ticker)
                        .thenComparing(AlertEventResponse.ReachedHolding::relationshipId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        Set<Long> distinctHoldingIds = reached.stream().map(AlertEventResponse.ReachedHolding::holdingId)
                .collect(Collectors.toSet());
        Set<Holding> exposureHoldings = distinctHoldingIds.stream().map(data.holdingById()::get)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        BigDecimal exposure = AlertBuilder.exposure(exposureHoldings, data.portfolioHoldings());
        List<AlertEventResponse.Source> sources = ordered.stream()
                .map(row -> source(row.article()))
                .toList();
        return new AlertEventResponse(alertIds, openOrDismissed(lead.alert().getStatus()), exposure,
                new AlertEventResponse.Headline(article(lead.article()), assessment(lead.assessment())), sources, reached);
    }

    private AlertResponse alertResponse(AlertRow row, PageData data) {
        List<AlertResponse.ReachedHolding> reached = data.reachedByAlert()
                .getOrDefault(row.alert().getId(), List.of()).stream()
                .map(found -> singleReachedHolding(found, data))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AlertResponse.ReachedHolding::ticker)
                        .thenComparing(AlertResponse.ReachedHolding::relationshipId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        return new AlertResponse(row.alert().getId(), openOrDismissed(row.alert().getStatus()),
                row.alert().getExposure(), row.alert().getCreatedAt(),
                new AlertResponse.Article(row.article().getTitle(), row.article().getSource(),
                        row.article().getPublishedAt(), row.article().getUrl()),
                new AlertResponse.Assessment(row.assessment().getEventType(), row.assessment().getSeverity(),
                        row.assessment().getConfidence(), row.assessment().getSummary(), row.assessment().getReason()),
                reached);
    }

    /** Groups chronologically, anchoring each three-day window at its earliest article. */
    private List<EventGroup> group(List<AlertRow> rows) {
        Map<GroupKey, List<AlertRow>> byEvent = new HashMap<>();
        for (AlertRow row : rows) {
            GroupKey key = new GroupKey(row.article().getCompanyId(), row.assessment().getEventType());
            byEvent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<EventGroup> events = new ArrayList<>();
        for (List<AlertRow> candidates : byEvent.values()) {
            List<AlertRow> chronological = candidates.stream()
                    .sorted(Comparator.comparing(row -> row.article().getPublishedAt()))
                    .toList();
            List<AlertRow> current = new ArrayList<>();
            for (AlertRow row : chronological) {
                if (!current.isEmpty() && Duration.between(current.getFirst().article().getPublishedAt(),
                        row.article().getPublishedAt()).compareTo(EVENT_WINDOW) > 0) {
                    events.add(new EventGroup(List.copyOf(current)));
                    current.clear();
                }
                current.add(row);
            }
            if (!current.isEmpty()) events.add(new EventGroup(List.copyOf(current)));
        }
        return events;
    }

    private AlertEventResponse.ReachedHolding eventReachedHolding(AlertHolding found, PageData data) {
        Holding holding = data.holdingById().get(found.getHoldingId());
        if (holding == null) return null;
        if (found.getRelationshipId() == null) {
            return new AlertEventResponse.ReachedHolding(
                    holding.getId(), holding.getTicker(), holding.getWeight(), null, null, null, null);
        }
        Relationship relationship = data.relationshipById().get(found.getRelationshipId());
        if (relationship == null) return null;
        Long supplierId = relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCounterpartyId() : relationship.getCompanyId();
        Long customerId = relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCompanyId() : relationship.getCounterpartyId();
        return new AlertEventResponse.ReachedHolding(holding.getId(), holding.getTicker(), holding.getWeight(),
                nameOf(data.companyById().get(supplierId)), nameOf(data.companyById().get(customerId)),
                relationship.getReviewStatus(), relationship.getId());
    }

    private AlertResponse.ReachedHolding singleReachedHolding(AlertHolding found, PageData data) {
        AlertEventResponse.ReachedHolding reached = eventReachedHolding(found, data);
        if (reached == null) return null;
        return new AlertResponse.ReachedHolding(reached.holdingId(), reached.ticker(), reached.weight(),
                reached.supplierName(), reached.customerName(), reached.reviewStatus(), reached.relationshipId());
    }

    private static AlertEventResponse.Article article(NewsArticle article) {
        return new AlertEventResponse.Article(article.getTitle(), article.getSource(), article.getPublishedAt(),
                article.getUrl());
    }

    private static AlertEventResponse.Source source(NewsArticle article) {
        return new AlertEventResponse.Source(article.getTitle(), article.getSource(), article.getPublishedAt(),
                article.getUrl());
    }

    private static AlertEventResponse.Assessment assessment(NewsAssessment assessment) {
        return new AlertEventResponse.Assessment(assessment.getEventType(), assessment.getSeverity(),
                assessment.getConfidence(), assessment.getSummary(), assessment.getReason());
    }

    /** The company's name as every page shows it. */
    private String nameOf(Company company) {
        return company == null ? null : displayNames.of(company);
    }

    private static AlertRow lead(List<AlertRow> rows) {
        return rows.stream().min(LEAD_ORDER).orElseThrow();
    }

    private static final Comparator<AlertRow> LEAD_ORDER = Comparator
            .comparingInt((AlertRow row) -> severityRank(row.assessment().getSeverity()))
            .thenComparing(row -> row.article().getPublishedAt(), Comparator.reverseOrder());

    private static final Comparator<AlertEventResponse> EVENT_ORDER = Comparator
            .comparingInt((AlertEventResponse event) -> severityRank(event.headline().assessment().severity()))
            .thenComparing(AlertEventResponse::exposure, Comparator.reverseOrder())
            .thenComparing(event -> event.headline().article().publishedAt(), Comparator.reverseOrder());

    private static int severityRank(Severity severity) {
        return switch (severity) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    /** An alert is open until dismissed; legacy acknowledged rows are returned as open. */
    private static AlertStatus openOrDismissed(AlertStatus status) {
        return status == AlertStatus.ACKNOWLEDGED ? AlertStatus.NEW : status;
    }

    private static void rejectAcknowledged(AlertStatus status) {
        if (status == AlertStatus.ACKNOWLEDGED) throw ApiException.badRequest(OPEN_OR_DISMISSED);
    }

    private void requirePortfolio(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound(PORTFOLIO_NOT_FOUND));
    }

    private record GroupKey(Long companyId, EventType eventType) {
    }

    private record EventGroup(List<AlertRow> rows) {
    }

    private record HoldingRelationshipKey(Long holdingId, Long relationshipId) {
    }

    private record PageData(List<Holding> portfolioHoldings, Map<Long, Holding> holdingById,
                            Map<Long, Relationship> relationshipById, Map<Long, Company> companyById,
                            Map<Long, List<AlertHolding>> reachedByAlert) {
        static PageData empty(List<Holding> portfolioHoldings) {
            return new PageData(portfolioHoldings, Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
