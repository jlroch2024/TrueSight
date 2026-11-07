package com.truesight.briefing;

import com.truesight.news.AlertEventResponse;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.Severity;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.Criticality;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The words the website shows for stored values, so the briefing reads as the screen does. Each method names the
 * website code it follows ({@code format.ts}, {@code view.ts}, the Inspector). When one changes, change it here too:
 * {@code BriefingTextTest} checks that every kind of event, severity and source has a label.
 */
final class BriefingText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private BriefingText() {
    }

    /** {@code formatDate}: "Oct 18, 2026"; a dash when there is no date. */
    static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    /** {@code EVENT_TYPE_LABELS}. */
    static String eventType(EventType type) {
        return switch (type) {
            case PRODUCTION_STOP -> "Production Stop";
            case SUPPLY_SHORTAGE -> "Supply Shortage";
            case LABOUR_ACTION -> "Labour Action";
            case NATURAL_DISASTER -> "Natural Disaster";
            case TRADE_RESTRICTION -> "Trade Restriction";
            case FINANCIAL_DISTRESS -> "Financial Distress";
            case LEGAL_OR_REGULATORY -> "Legal or Regulatory";
            case CYBER_ATTACK -> "Cyber Attack";
            case OTHER_DISRUPTION -> "Other Disruption";
        };
    }

    /** {@code SEVERITY_LABELS}. */
    static String severity(Severity severity) {
        return switch (severity) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
        };
    }

    /** {@code SEVERITY_LABELS}, which the website also uses for confidence. */
    static String confidence(Confidence confidence) {
        return switch (confidence) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
        };
    }

    /** {@code STATUS_LABELS}. */
    static String status(HoldingStatus status) {
        return switch (status) {
            case WAITING -> "Waiting";
            case ANALYSING -> "Analysing";
            case DONE -> "Done";
            case FAILED -> "Failed";
            case NO_REPORT_FOUND -> "No Report Found";
        };
    }

    /** The Inspector's "Sole Source" and "Single Source" tags. */
    static String criticality(Criticality criticality) {
        return switch (criticality) {
            case SOLE_SOURCE -> "Sole Source";
            case SINGLE_SOURCE -> "Single Source";
        };
    }

    /** A weight as the Holdings tab shows it: "12.5%", or "Weight Not Provided". */
    static String weight(BigDecimal weight) {
        return weight == null ? "Weight Not Provided" : number(weight) + "%";
    }

    /** {@code exposureOf}, the figure: "30%" for a portfolio with weights, otherwise a count of holdings. */
    static String exposureFigure(BigDecimal exposure, boolean weighted) {
        return number(exposure) + (weighted ? "%" : "");
    }

    /** {@code exposureOf}, the label: "of Portfolio", or "of 10 Holdings" (one Holding). */
    static String exposureLabel(boolean weighted, int holdingCount) {
        if (weighted) {
            return "of Portfolio";
        }
        return "of " + holdingCount + (holdingCount == 1 ? " Holding" : " Holdings");
    }

    /** The Inspector's "1 Direct, 2 Indirect"; "Indirect" is left out when there is none. */
    static String reachSplit(List<AlertEventResponse.ReachedHolding> reached) {
        long indirect = reached.stream().filter(AlertEventResponse.ReachedHolding::indirect).count();
        String text = (reached.size() - indirect) + " Direct";
        return indirect > 0 ? text + ", " + indirect + " Indirect" : text;
    }

    /** "1 Holding Reached" or "3 Holdings Reached". */
    static String reachedCount(int count) {
        return count + (count == 1 ? " Holding Reached" : " Holdings Reached");
    }

    /**
     * How an event reaches one holding, from the names the Inspector draws: "A supplies B", or "A supplies B, which
     * supplies C" in two steps. An event about the holding itself reads "Named in the Article".
     */
    static String path(AlertEventResponse.ReachedHolding reached) {
        if (reached.supplierName() == null) {
            return "Named in the Article";
        }
        String customer = reached.customerName() == null ? "" : reached.customerName();
        if (reached.middleName() == null) {
            return reached.supplierName() + " supplies " + customer;
        }
        return reached.supplierName() + " supplies " + reached.middleName() + ", which supplies " + customer;
    }

    // Figures are shown to one decimal with a whole number's ".0" left off, as the website does (toFixed(1)).
    private static String number(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
