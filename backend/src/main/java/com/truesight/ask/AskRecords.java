package com.truesight.ask;

import com.truesight.news.AlertEventResponse;
import com.truesight.news.AlertsResponse;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.RelationshipConfidence;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The portfolio's data as numbered records, the only thing Gemini may answer from: holdings {@code [H1]}, relationships
 * {@code [R1]} and open alerts {@code [A1]}. The numbers count from 1 in each prompt (they are not database ids), and
 * {@link Built#byPromptId} says which real item each one is, so a source Gemini names can be checked and opened.
 *
 * <p>Everything written into a record that came from a report, a headline or an AI is untrusted text: it is put on one
 * line, its quotation marks and any tags are taken out, and it is cut short (see {@link #oneLine}).
 */
final class AskRecords {

    /** The longest piece of free text (one sentence of evidence, one headline) a record carries. */
    static final int LONGEST_TEXT = 400;
    private static final int LONGEST_LABEL = 80;

    /** The most records of each kind one prompt carries, so a very large portfolio cannot make it too long to send. */
    static final int MOST_HOLDINGS = 200;
    static final int MOST_RELATIONSHIPS = 300;
    static final int MOST_ALERTS = 50;

    private AskRecords() {
    }

    /**
     * The records as text, the item behind each prompt id, and which relationship ids are unreviewed.
     */
    record Built(String text, Map<String, AskResponse.Source> byPromptId, Set<String> unreviewed) {
    }

    static Built build(List<HoldingResponse> holdings, List<RelationshipResponse> relationships, AlertsResponse alerts) {
        Map<String, AskResponse.Source> byPromptId = new LinkedHashMap<>();
        Set<String> unreviewed = new TreeSet<>();
        StringBuilder text = new StringBuilder();

        text.append("Holdings (").append(shown(holdings.size(), MOST_HOLDINGS)).append("):\n");
        if (holdings.isEmpty()) {
            text.append("none\n");
        }
        for (int i = 0; i < Math.min(holdings.size(), MOST_HOLDINGS); i++) {
            HoldingResponse holding = holdings.get(i);
            String id = "H" + (i + 1);
            byPromptId.put(id, new AskResponse.Source(AskResponse.Kind.HOLDING, holding.id(), holdingName(holding)));
            text.append('[').append(id).append("] ").append(holdingLine(holding)).append('\n');
        }

        text.append("\nRelationships (").append(shown(relationships.size(), MOST_RELATIONSHIPS)).append("):\n");
        if (relationships.isEmpty()) {
            text.append("none\n");
        }
        Map<Long, String> relationshipIds = new HashMap<>();
        for (int i = 0; i < Math.min(relationships.size(), MOST_RELATIONSHIPS); i++) {
            RelationshipResponse relationship = relationships.get(i);
            String id = "R" + (i + 1);
            relationshipIds.put(relationship.id(), id);
            byPromptId.put(id, new AskResponse.Source(AskResponse.Kind.RELATIONSHIP, relationship.id(),
                    oneLine(supplier(relationship) + " supplies " + customer(relationship), LONGEST_LABEL)));
            if (relationship.reviewStatus() == ReviewStatus.UNREVIEWED) {
                unreviewed.add(id);
            }
            text.append('[').append(id).append("] ").append(relationshipLine(relationship)).append('\n');
        }

        List<AlertEventResponse> events = alerts.alerts();
        text.append("\nOpen alerts (").append(shown(events.size(), MOST_ALERTS)).append("):\n");
        if (events.isEmpty()) {
            text.append("none\n");
        }
        for (int i = 0; i < Math.min(events.size(), MOST_ALERTS); i++) {
            AlertEventResponse event = events.get(i);
            String id = "A" + (i + 1);
            byPromptId.put(id, new AskResponse.Source(AskResponse.Kind.ALERT, event.alertIds().get(0),
                    oneLine(event.headline().article().title(), LONGEST_LABEL)));
            text.append('[').append(id).append("] ").append(alertLine(event, alerts, relationshipIds)).append('\n');
        }
        return new Built(text.toString(), byPromptId, unreviewed);
    }

    /** "12", or "first 300 of 450" when there are more than fit, so Gemini knows the list is not the whole of it. */
    private static String shown(int count, int most) {
        return count <= most ? String.valueOf(count) : "first " + most + " of " + count;
    }

    private static String holdingLine(HoldingResponse holding) {
        StringBuilder line = new StringBuilder(holdingName(holding)).append(", ")
                .append(holding.weight() == null ? "weight not given" : number(holding.weight()) + "% of the portfolio")
                .append(". Analysis: ").append(statusWords(holding.status())).append('.');
        if (holding.status() != HoldingStatus.DONE && holding.statusReason() != null) {
            line.append(" Reason: ").append(oneLine(holding.statusReason(), LONGEST_TEXT)).append('.');
        }
        if (holding.reportType() != null) {
            line.append(" Latest report: ").append(holding.reportType());
            if (holding.reportDate() != null) {
                line.append(", ").append(holding.reportDate());
            }
            line.append('.');
        }
        return line.toString();
    }

    private static String holdingName(HoldingResponse holding) {
        String name = holding.companyDisplayName() != null ? holding.companyDisplayName() : holding.companyName();
        return name == null ? holding.ticker() : oneLine(name, LONGEST_LABEL) + " (" + holding.ticker() + ")";
    }

    /** "A supplies B: what, Evidence: "…". Source: 10-K filed 2026-02-25. Unreviewed. Medium confidence." */
    private static String relationshipLine(RelationshipResponse relationship) {
        StringBuilder line = new StringBuilder(supplier(relationship)).append(" supplies ").append(customer(relationship));
        if (relationship.provides() != null && !relationship.provides().isBlank()) {
            line.append(": ").append(oneLine(relationship.provides(), LONGEST_TEXT));
        }
        line.append(". Evidence: \"").append(oneLine(relationship.evidence(), LONGEST_TEXT)).append("\".");
        if (relationship.report() != null) {
            line.append(" Source: ").append(relationship.report().form()).append(" filed ")
                    .append(relationship.report().filingDate()).append('.');
        }
        if (relationship.figure() != null) {
            line.append(' ').append(figureWords(relationship));
        }
        line.append(' ').append(reviewWords(relationship));
        RelationshipConfidence confidence = relationship.confidence();
        if (confidence != null) {
            line.append(' ').append(capitalise(confidence.level().name())).append(" confidence.");
            String reasons = confidence.reasons().stream()
                    .filter(reason -> reason.effect() != RelationshipConfidence.Effect.NONE)
                    .map(reason -> oneLine(reason.text(), LONGEST_TEXT))
                    .collect(Collectors.joining("; "));
            if (!reasons.isEmpty()) {
                line.append(" Confidence notes: ").append(reasons).append('.');
            }
        }
        if (relationship.criticality() != null) {
            line.append(relationship.criticality().name().equals("SOLE_SOURCE") ? " Sole source" : " Single source");
            if (relationship.criticalityEvidence() != null) {
                line.append(": ").append(oneLine(relationship.criticalityEvidence(), LONGEST_TEXT));
            }
            line.append('.');
        }
        if (relationship.reviewNote() != null && !relationship.reviewNote().isBlank()) {
            line.append(" Analyst note: \"").append(oneLine(relationship.reviewNote(), LONGEST_TEXT)).append("\".");
        }
        return line.toString();
    }

    /** "91% of Cirrus Logic's revenue in fiscal 2026 (tagged in its 10-K)." The figure is of the report's own company. */
    private static String figureWords(RelationshipResponse relationship) {
        RelationshipResponse.Figure figure = relationship.figure();
        int percent = figure.share().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        String measure = figure.measure() == com.truesight.report.ConcentrationMeasure.REVENUE ? "revenue" : "purchases";
        String form = relationship.report() == null ? "annual report" : relationship.report().form();
        return "%d%% of %s's %s in fiscal %d (tagged in its %s).".formatted(percent, relationship.company().displayName(),
                measure, figure.periodEnd().getYear(), form);
    }

    private static String reviewWords(RelationshipResponse relationship) {
        return switch (relationship.reviewStatus()) {
            case UNREVIEWED -> "Unreviewed.";
            case REJECTED -> "Rejected.";
            case APPROVED -> relationship.reviewedAt() == null
                    ? "Approved."
                    : "Approved by an analyst on " + day(relationship.reviewedAt()) + ".";
        };
    }

    private static String alertLine(AlertEventResponse event, AlertsResponse alerts, Map<Long, String> relationshipIds) {
        AlertEventResponse.Article article = event.headline().article();
        AlertEventResponse.Assessment assessment = event.headline().assessment();
        StringBuilder line = new StringBuilder(capitalise(assessment.severity().name()))
                .append(" severity ").append(assessment.eventType().name().replace('_', ' ').toLowerCase())
                .append(". Headline: \"").append(oneLine(article.title(), LONGEST_TEXT)).append("\" (")
                .append(article.source() == null ? "source not recorded" : oneLine(article.source(), LONGEST_LABEL))
                .append(", ").append(day(article.publishedAt())).append(").");
        if (assessment.summary() != null && !assessment.summary().isBlank()) {
            line.append(" Summary: ").append(oneLine(assessment.summary(), LONGEST_TEXT));
        }
        if (assessment.reason() != null && !assessment.reason().isBlank()) {
            line.append(" Reason: ").append(oneLine(assessment.reason(), LONGEST_TEXT));
        }
        line.append(" Exposure: ").append(alerts.weighted()
                ? number(event.exposure()) + "% of the portfolio."
                : number(event.exposure()) + " of " + alerts.holdingCount() + " holdings.");
        List<String> reached = new ArrayList<>();
        for (AlertEventResponse.ReachedHolding holding : event.holdings()) {
            reached.add(reachedWords(holding, relationshipIds));
        }
        line.append(" Reaches: ").append(reached.isEmpty() ? "no holding" : String.join(", ", reached)).append('.');
        line.append(event.recommendation() == null || event.recommendation().isBlank()
                ? " No approved next step."
                : " Approved next step: " + oneLine(event.recommendation(), LONGEST_TEXT));
        return line.toString();
    }

    private static String reachedWords(AlertEventResponse.ReachedHolding holding, Map<Long, String> relationshipIds) {
        StringBuilder words = new StringBuilder(holding.ticker());
        if (holding.weight() != null) {
            words.append(' ').append(number(holding.weight())).append('%');
        }
        if (holding.supplierName() == null) {
            return words.append(" (named in the article)").toString();
        }
        List<String> via = new ArrayList<>();
        for (Long relationshipId : java.util.Arrays.asList(holding.relationshipId(), holding.secondRelationshipId())) {
            if (relationshipId != null && relationshipIds.containsKey(relationshipId)) {
                via.add(relationshipIds.get(relationshipId));
            }
        }
        words.append(holding.indirect() ? " (indirectly" : " (directly");
        if (!via.isEmpty()) {
            words.append(" via ").append(String.join(", ", via));
        }
        return words.append(')').toString();
    }

    private static String supplier(RelationshipResponse relationship) {
        return (relationship.type() == RelationshipType.SUPPLIER ? relationship.counterparty() : relationship.company())
                .displayName();
    }

    private static String customer(RelationshipResponse relationship) {
        return (relationship.type() == RelationshipType.SUPPLIER ? relationship.company() : relationship.counterparty())
                .displayName();
    }

    private static String statusWords(HoldingStatus status) {
        return switch (status) {
            case WAITING -> "Waiting";
            case ANALYSING -> "Analysing";
            case DONE -> "Done";
            case FAILED -> "Failed";
            case NO_REPORT_FOUND -> "No report found";
        };
    }

    /**
     * Free text as one line that cannot close the records or open the question: whitespace and line breaks become single
     * spaces, quotation marks become apostrophes, tags are dropped, and long text is cut with an ellipsis.
     */
    static String oneLine(String text, int longest) {
        if (text == null) {
            return "";
        }
        String line = text.replaceAll("</?(records|question|earlier_conversation)>", " ")
                .replace('"', '\'').replace('“', '\'').replace('”', '\'')
                .replaceAll("\\s+", " ").strip();
        return line.length() <= longest ? line : line.substring(0, longest - 1).stripTrailing() + "…";
    }

    /** A figure to one decimal with a whole number's ".0" left off, as the website shows it: 42.5, 40. */
    private static String number(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String capitalise(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static LocalDate day(Instant moment) {
        return moment.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
