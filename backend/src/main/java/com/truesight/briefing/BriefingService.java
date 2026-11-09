package com.truesight.briefing;

import com.truesight.common.ApiException;
import com.truesight.news.AlertService;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.portfolio.HoldingService;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.relationship.RelationshipService;
import com.truesight.report.ConcentrationMeasure;
import com.truesight.report.ConcentrationResponse;
import com.truesight.report.ConcentrationService;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
    private final HoldingRepository holdingRows;
    private final ConcentrationService concentrations;

    public BriefingService(PortfolioRepository portfolios, HoldingService holdings, AlertService alerts,
                           RelationshipService relationships, CurrentUser currentUser, BriefingPdf pdf,
                           HoldingRepository holdingRows, ConcentrationService concentrations) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.alerts = alerts;
        this.relationships = relationships;
        this.currentUser = currentUser;
        this.pdf = pdf;
        this.holdingRows = holdingRows;
        this.concentrations = concentrations;
    }

    @Transactional(readOnly = true)
    public Briefing build(Long portfolioId, String graphPng, String timeZone) {
        Portfolio portfolio = portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Portfolio not found."));
        // The reader's own time zone, the one the website shows alert dates in, so the cover, the alerts and the file
        // name all name the day the reader is in.
        ZoneId zone = zoneOf(timeZone);
        LocalDate today = LocalDate.now(zone);

        BriefingData data = new BriefingData(
                portfolio.getName(),
                today,
                zone,
                decode(graphPng),
                byWeight(holdings.list(portfolioId)),
                // Open alerts only, already most severe first, with their reach, exposure and approved next step.
                alerts.list(portfolioId, false),
                // The sole- and single-source dependencies among the relationships the graph draws.
                relationships.forPortfolio(portfolioId).stream().filter(link -> link.criticality() != null).toList(),
                biggestDependencies(portfolioId));

        return new Briefing(BriefingFilename.of(portfolio.getName(), today), pdf.write(data));
    }

    /** Each holding's largest named revenue or purchases figure, biggest first. A holding with none is left out. */
    private List<BriefingData.BiggestDependency> biggestDependencies(Long portfolioId) {
        List<BriefingData.BiggestDependency> biggest = new ArrayList<>();
        for (Holding holding : holdingRows.findByPortfolioIdOrderByTickerAsc(portfolioId)) {
            if (holding.getCompanyId() == null) continue;
            biggestOf(holding.getTicker(), concentrations.forCompany(holding.getCompanyId())).ifPresent(biggest::add);
        }
        biggest.sort(Comparator.comparing(BriefingData.BiggestDependency::percent).reversed());
        return biggest;
    }

    /** The largest named revenue or purchases figure of the latest period the report tags; empty when there is none. */
    static Optional<BriefingData.BiggestDependency> biggestOf(String holding, List<ConcentrationResponse> figures) {
        List<ConcentrationResponse> named = figures.stream()
                .filter(figure -> figure.counterparty() != null)
                .filter(figure -> figure.measure() == ConcentrationMeasure.REVENUE
                        || figure.measure() == ConcentrationMeasure.PURCHASES)
                .toList();
        return named.stream().map(ConcentrationResponse::periodEnd).max(Comparator.naturalOrder())
                .flatMap(latest -> named.stream().filter(figure -> figure.periodEnd().equals(latest))
                        .max(Comparator.comparing(ConcentrationResponse::share)))
                .map(figure -> new BriefingData.BiggestDependency(holding, figure.counterparty().displayName(),
                        figure.share().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue(),
                        figure.measure() == ConcentrationMeasure.REVENUE ? "Revenue" : "Purchases",
                        figure.periodEnd().getYear(),
                        figure.report() == null ? "Annual Report" : figure.report().form(),
                        figure.report() == null ? null : figure.report().filingDate()));
    }

    /**
     * The picture's bytes, or null when there is none or it cannot be read: a bad picture is left out of the briefing
     * rather than stopping it.
     */
    /** The time zone named (such as {@code Asia/Singapore}), or UTC when none is sent or the name is not one. */
    static ZoneId zoneOf(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone.strip());
        } catch (DateTimeException exception) {
            return ZoneOffset.UTC;
        }
    }

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
