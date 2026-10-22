package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Lists and changes one portfolio's alerts. */
@Service
public class AlertService {

    private static final String PORTFOLIO_NOT_FOUND = "Portfolio not found.";
    private static final String ALERT_NOT_FOUND = "Alert not found.";
    private static final String OPEN_OR_DISMISSED = "An alert is either open or dismissed.";

    private final PortfolioRepository portfolios;
    private final AlertRepository alerts;
    private final AlertHoldingRepository alertHoldings;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final CurrentUser currentUser;

    public AlertService(PortfolioRepository portfolios, AlertRepository alerts,
                        AlertHoldingRepository alertHoldings, HoldingRepository holdings,
                        RelationshipRepository relationships, CompanyRepository companies,
                        CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.alerts = alerts;
        this.alertHoldings = alertHoldings;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public AlertsResponse list(Long portfolioId, boolean includeDismissed) {
        requirePortfolio(portfolioId);
        List<AlertRow> rows = alerts.findOrdered(portfolioId, includeDismissed);
        List<Holding> held = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
        return new AlertsResponse(AlertBuilder.weighted(held), held.size(), responses(rows));
    }

    @Transactional
    public AlertResponse setStatus(Long portfolioId, Long alertId, AlertStatus status) {
        if (status == AlertStatus.ACKNOWLEDGED) throw ApiException.badRequest(OPEN_OR_DISMISSED);
        requirePortfolio(portfolioId);
        Alert alert = alerts.findByIdAndPortfolioId(alertId, portfolioId)
                .orElseThrow(() -> ApiException.notFound(ALERT_NOT_FOUND));
        alert.setStatus(status);
        alerts.save(alert);
        AlertRow row = alerts.findOrdered(portfolioId, true).stream()
                .filter(found -> found.alert().getId().equals(alertId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(ALERT_NOT_FOUND));
        return responses(List.of(row)).getFirst();
    }

    private List<AlertResponse> responses(List<AlertRow> rows) {
        if (rows.isEmpty()) return List.of();

        List<Long> alertIds = rows.stream().map(row -> row.alert().getId()).toList();
        List<AlertHolding> reached = alertHoldings.findByAlertIdIn(alertIds);
        Map<Long, Holding> holdingById = holdings.findAllById(
                        reached.stream().map(AlertHolding::getHoldingId).distinct().toList())
                .stream().collect(Collectors.toMap(Holding::getId, Function.identity()));
        Map<Long, Relationship> relationshipById = relationships.findAllById(
                        reached.stream().map(AlertHolding::getRelationshipId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Relationship::getId, Function.identity()));

        Set<Long> companyIds = relationshipById.values().stream()
                .flatMap(relationship -> Stream.of(
                        relationship.getCompanyId(), relationship.getCounterpartyId()))
                .collect(Collectors.toSet());
        Map<Long, Company> companyById = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, List<AlertHolding>> reachedByAlert = reached.stream()
                .collect(Collectors.groupingBy(AlertHolding::getAlertId));

        return rows.stream().map(row -> {
            Alert alert = row.alert();
            NewsArticle article = row.article();
            NewsAssessment assessment = row.assessment();
            List<AlertResponse.ReachedHolding> reachedHoldings = reachedByAlert
                    .getOrDefault(alert.getId(), List.of()).stream()
                    .map(found -> reachedHolding(found, holdingById, relationshipById, companyById))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(AlertResponse.ReachedHolding::ticker))
                    .toList();
            return new AlertResponse(
                    alert.getId(), openOrDismissed(alert.getStatus()), alert.getExposure(), alert.getCreatedAt(),
                    new AlertResponse.Article(article.getTitle(), article.getSource(), article.getPublishedAt(),
                            article.getUrl()),
                    new AlertResponse.Assessment(assessment.getEventType(), assessment.getSeverity(),
                            assessment.getConfidence(), assessment.getSummary(), assessment.getReason()),
                    reachedHoldings);
        }).toList();
    }

    private AlertResponse.ReachedHolding reachedHolding(
            AlertHolding found,
            Map<Long, Holding> holdingById,
            Map<Long, Relationship> relationshipById,
            Map<Long, Company> companyById) {
        Holding holding = holdingById.get(found.getHoldingId());
        if (holding == null) return null;
        if (found.getRelationshipId() == null) {
            return new AlertResponse.ReachedHolding(
                    holding.getId(), holding.getTicker(), holding.getWeight(), null, null, null);
        }
        Relationship relationship = relationshipById.get(found.getRelationshipId());
        if (relationship == null) return null;
        Long supplierId = relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCounterpartyId() : relationship.getCompanyId();
        Long customerId = relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCompanyId() : relationship.getCounterpartyId();
        return new AlertResponse.ReachedHolding(holding.getId(), holding.getTicker(), holding.getWeight(),
                nameOf(companyById.get(supplierId)), nameOf(companyById.get(customerId)),
                relationship.getReviewStatus());
    }

    private static String nameOf(Company company) {
        return company == null ? null : company.getName();
    }

    /** An alert is open until it is dismissed. Rows stored as Acknowledged, from before that rule, read as New. */
    private static AlertStatus openOrDismissed(AlertStatus status) {
        return status == AlertStatus.ACKNOWLEDGED ? AlertStatus.NEW : status;
    }

    private void requirePortfolio(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound(PORTFOLIO_NOT_FOUND));
    }
}
