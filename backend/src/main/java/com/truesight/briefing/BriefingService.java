package com.truesight.briefing;

import com.truesight.common.ApiException;
import com.truesight.news.AlertService;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.portfolio.HoldingService;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.relationship.RelationshipService;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Makes a portfolio's briefing from what the screen already shows. Holdings, open alerts and dependencies come from
 * the services that serve them, so the briefing can never say anything the app does not. Nothing is stored.
 */
@Service
public class BriefingService {

    private static final String PNG_START = "base64,";

    private final PortfolioRepository portfolios;
    private final HoldingService holdings;
    private final AlertService alerts;
    private final RelationshipService relationships;
    private final CurrentUser currentUser;
    private final BriefingPdf pdf;

    public BriefingService(PortfolioRepository portfolios, HoldingService holdings, AlertService alerts,
                           RelationshipService relationships, CurrentUser currentUser, BriefingPdf pdf) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.alerts = alerts;
        this.relationships = relationships;
        this.currentUser = currentUser;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true)
    public Briefing build(Long portfolioId, String graphPng) {
        Portfolio portfolio = portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
        LocalDate today = LocalDate.now();

        BriefingData data = new BriefingData(
                portfolio.getName(),
                today,
                decode(graphPng),
                byWeight(holdings.list(portfolioId)),
                // Open alerts only, already most severe first, with their reach, exposure and approved next step.
                alerts.list(portfolioId, false),
                // The sole- and single-source dependencies among the relationships the graph draws.
                relationships.forPortfolio(portfolioId).stream().filter(link -> link.criticality() != null).toList());

        return new Briefing(BriefingFilename.of(portfolio.getName(), today), pdf.write(data));
    }

    /**
     * The picture's bytes, or null when there is none or it cannot be read: a bad picture is left out of the briefing
     * rather than stopping it.
     */
    static byte[] decode(String graphPng) {
        if (graphPng == null || graphPng.isBlank()) {
            return null;
        }
        String base64 = graphPng.contains(PNG_START)
                ? graphPng.substring(graphPng.indexOf(PNG_START) + PNG_START.length())
                : graphPng;
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(base64.trim());
            return bytes.length == 0 ? null : bytes;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The Holdings tab's order: the largest weight first, the same weight by ticker, those without a weight last. */
    static List<HoldingResponse> byWeight(List<HoldingResponse> held) {
        return held.stream()
                .sorted(Comparator
                        .comparing(HoldingResponse::weight, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(HoldingResponse::ticker))
                .toList();
    }
}
