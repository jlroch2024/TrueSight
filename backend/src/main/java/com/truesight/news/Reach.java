package com.truesight.news;

import com.truesight.portfolio.Holding;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which of a portfolio's holdings a company's trouble reaches, along the supply graph. The one rule behind both an
 * alert (a news event at a company) and the Disruption Simulator (a company that stops supplying).
 *
 * <p>Company E reaches holding H when H is E itself, or when a relationship joins them one step along the graph, or
 * when E supplies a company that supplies H (two steps). Callers pass the relationships that count, with Rejected
 * ones already left out. If several relationships join the same pair, the Approved one is kept, then the lowest id.
 *
 * <p>The {@link Direction} says which way the first step may go.
 */
public final class Reach {

    /** Which way a company's trouble may travel on its first step. */
    public enum Direction {
        /**
         * An alert: one step either way, because an event at a customer also hurts its supplier (E supplies H, E buys
         * from H, H supplies E, or H buys from E), and two steps downstream.
         */
        EITHER_WAY,
        /** The simulator: only downstream, from a supplier to what it supplies, one step or two. */
        DOWNSTREAM
    }

    /** The link a holding is reached along: one relationship, or two for a holding reached in two steps. */
    public record Path(Long relationshipId, Long secondRelationshipId) {
    }

    /** Approved first, then the lowest id: the relationship kept when several join the same holding and company. */
    private static final Comparator<Relationship> PREFERRED = Comparator
            .comparing((Relationship relationship) -> relationship.getReviewStatus() != ReviewStatus.APPROVED)
            .thenComparing(Relationship::getId);
    private static final Comparator<PathCandidate> PREFERRED_PATH = Comparator
            .comparing(PathCandidate::first, PREFERRED)
            .thenComparing(PathCandidate::second, PREFERRED);

    private Reach() {
    }

    /**
     * Each holding that trouble at {@code from} reaches, in the order of {@code held}, with the relationships it
     * travels along. A holding that is {@code from} itself has a path of no relationships (both null).
     */
    public static Map<Holding, Path> reached(Long from, List<Holding> held, List<Relationship> links,
                                             Direction direction) {
        Map<Holding, Path> reached = new LinkedHashMap<>();
        for (Holding holding : held) {
            Long company = holding.getCompanyId();
            if (company.equals(from)) {
                reached.put(holding, new Path(null, null));
                continue;
            }
            links.stream()
                    .filter(link -> direction == Direction.EITHER_WAY
                            ? joins(link, company, from)
                            : supplies(link, from, company))
                    .min(PREFERRED)
                    .ifPresent(link -> reached.put(holding, new Path(link.getId(), null)));
        }
        for (Holding holding : held) {
            if (reached.containsKey(holding)) continue;
            Long company = holding.getCompanyId();
            links.stream()
                    .filter(second -> supplies(second, supplierOf(second), company))
                    .flatMap(second -> links.stream()
                            .filter(first -> supplies(first, from, supplierOf(second)))
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

    private record PathCandidate(Relationship first, Relationship second) {
    }
}
