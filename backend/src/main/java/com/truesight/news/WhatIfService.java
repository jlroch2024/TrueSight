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
import com.truesight.relationship.RelationshipResponse;
import com.truesight.relationship.RelationshipService;
import com.truesight.relationship.ReviewStatus;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Disruption Simulator: which of a portfolio's holdings reach trouble if one company stops supplying entirely.
 *
 * <p>It uses {@link Reach} downstream only: a holding the company supplies, a holding supplied by a company it
 * supplies, or the company itself when it is a holding. A holding that only supplies the company is not reached: it
 * loses a customer, which is a different risk. Rejected relationships are left out, and a relationship is the one in
 * the latest annual reports. No period is assumed: TrueSight has no stock or lead-time data, so every figure comes
 * from the graph and the weights.
 */
@Service
public class WhatIfService {

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final CompanyDisplayNames displayNames;
    private final RelationshipService relationshipService;
    private final CurrentUser currentUser;

    public WhatIfService(PortfolioRepository portfolios, HoldingRepository holdings,
                         RelationshipRepository relationships, CompanyRepository companies,
                         CompanyDisplayNames displayNames, RelationshipService relationshipService,
                         CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.displayNames = displayNames;
        this.relationshipService = relationshipService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public WhatIfResponse whatIf(Long portfolioId, Long companyId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
        Company stopped = companies.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Company not found."));

        List<Holding> held = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                .filter(holding -> holding.getCompanyId() != null)
                .toList();
        Set<Long> relevant = Stream.concat(held.stream().map(Holding::getCompanyId), Stream.of(companyId))
                .collect(Collectors.toSet());
        List<Relationship> links = relationships.findByCompanyIdInOrCounterpartyIdIn(relevant, relevant).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .toList();

        Map<Holding, Reach.Path> reached = Reach.reached(companyId, held, links, Reach.Direction.DOWNSTREAM);

        List<WhatIfResponse.ReachedHolding> reachedHoldings = new ArrayList<>();
        Set<Long> onPaths = new LinkedHashSet<>();
        reached.forEach((holding, path) -> {
            List<Long> ids = Stream.of(path.relationshipId(), path.secondRelationshipId())
                    .filter(Objects::nonNull)
                    .toList();
            onPaths.addAll(ids);
            reachedHoldings.add(new WhatIfResponse.ReachedHolding(holding.getId(), holding.getTicker(),
                    holding.getWeight(), path.secondRelationshipId() != null, ids));
        });

        List<Relationship> used = links.stream()
                .filter(link -> onPaths.contains(link.getId()))
                .sorted(Comparator.comparing(Relationship::getId))
                .toList();
        List<RelationshipResponse> usedResponses = relationshipService.toResponses(used);
        List<RelationshipResponse> critical = usedResponses.stream()
                .filter(link -> link.criticality() != null)
                .toList();

        boolean weighted = AlertBuilder.weighted(held);
        String name = displayNames.of(stopped);
        return new WhatIfResponse(
                new RelationshipResponse.CompanySummary(stopped.getId(), stopped.getName(), name,
                        stopped.getTicker()),
                reachedHoldings,
                AlertBuilder.exposure(reached.keySet(), held),
                weighted,
                held.size(),
                critical,
                usedResponses,
                assumptions(name, weighted));
    }

    /** What the answer assumes, written on every result. */
    private static String assumptions(String name, boolean weighted) {
        return "Assumes %s stops supplying entirely. Uses relationships from the latest annual reports; rejected ones "
                .formatted(name) + "are left out. "
                + (weighted ? "Weights from your CSV." : "This portfolio has no weights, so exposure counts holdings.");
    }
}
