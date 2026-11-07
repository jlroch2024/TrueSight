package com.truesight.briefing;

import com.truesight.news.AlertEventResponse.ReachedHolding;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.Severity;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.Criticality;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The briefing says things in the words the website uses. */
class BriefingTextTest {

    @Test
    void everyKindOfEventHasTheLabelTheWebsiteShowsForIt() throws IOException {
        Map<String, String> onScreen = labelsIn("EVENT_TYPE_LABELS");

        assertThat(onScreen).hasSize(EventType.values().length);
        for (EventType type : EventType.values()) {
            assertThat(BriefingText.eventType(type)).isEqualTo(onScreen.get(type.name()));
        }
    }

    @Test
    void severityAndConfidenceAreLowMediumOrHighAsOnScreen() throws IOException {
        Map<String, String> onScreen = labelsIn("SEVERITY_LABELS");

        for (Severity severity : Severity.values()) {
            assertThat(BriefingText.severity(severity)).isEqualTo(onScreen.get(severity.name()));
        }
        for (Confidence confidence : Confidence.values()) {
            assertThat(BriefingText.confidence(confidence)).isEqualTo(onScreen.get(confidence.name()));
        }
    }

    @Test
    void everyHoldingStatusHasTheLabelTheWebsiteShowsForIt() throws IOException {
        Map<String, String> onScreen = labelsIn("STATUS_LABELS");

        assertThat(onScreen).hasSize(HoldingStatus.values().length);
        for (HoldingStatus status : HoldingStatus.values()) {
            assertThat(BriefingText.status(status)).isEqualTo(onScreen.get(status.name()));
        }
    }

    @Test
    void soleAndSingleSourceAreNamedAsTheInspectorNamesThem() {
        assertThat(BriefingText.criticality(Criticality.SOLE_SOURCE)).isEqualTo("Sole Source");
        assertThat(BriefingText.criticality(Criticality.SINGLE_SOURCE)).isEqualTo("Single Source");
    }

    @Test
    void exposureIsAShareOfThePortfolioWhenTheHoldingsHaveWeights() {
        assertThat(BriefingText.exposureFigure(new BigDecimal("42.5000"), true)).isEqualTo("42.5%");
        assertThat(BriefingText.exposureFigure(new BigDecimal("40.0000"), true)).isEqualTo("40%");
        assertThat(BriefingText.exposureFigure(new BigDecimal("33.3333"), true)).isEqualTo("33.3%");
        assertThat(BriefingText.exposureLabel(true, 10)).isEqualTo("of Portfolio");
    }

    @Test
    void exposureIsACountOfHoldingsWhenNoneHasAWeight() {
        assertThat(BriefingText.exposureFigure(new BigDecimal("2.0000"), false)).isEqualTo("2");
        assertThat(BriefingText.exposureLabel(false, 10)).isEqualTo("of 10 Holdings");
        assertThat(BriefingText.exposureLabel(false, 1)).isEqualTo("of 1 Holding");
    }

    @Test
    void aWeightIsAPercentOrSaysItWasNotProvided() {
        assertThat(BriefingText.weight(new BigDecimal("12.5000"))).isEqualTo("12.5%");
        assertThat(BriefingText.weight(new BigDecimal("10.0000"))).isEqualTo("10%");
        assertThat(BriefingText.weight(null)).isEqualTo("Weight Not Provided");
    }

    @Test
    void datesReadLikeTheWebsitesWhateverTheMachinesLanguage() {
        assertThat(BriefingText.date(LocalDate.parse("2026-02-25"))).isEqualTo("Feb 25, 2026");
        assertThat(BriefingText.date(LocalDate.parse("2026-09-03"))).isEqualTo("Sep 3, 2026");
        assertThat(BriefingText.date(null)).isEqualTo("-");
    }

    @Test
    void aPathNamesTheCompaniesInOneOrTwoSteps() {
        assertThat(BriefingText.path(reached(false, "Carl Zeiss SMT", null, "ASML Holding")))
                .isEqualTo("Carl Zeiss SMT supplies ASML Holding");
        assertThat(BriefingText.path(reached(true, "Carl Zeiss SMT", "ASML Holding", "TSMC")))
                .isEqualTo("Carl Zeiss SMT supplies ASML Holding, which supplies TSMC");
        assertThat(BriefingText.path(reached(false, null, null, null))).isEqualTo("Named in the Article");
    }

    @Test
    void reachCountsDirectAndIndirectHoldingsLeavingOutIndirectWhenThereIsNone() {
        ReachedHolding direct = reached(false, "A", null, "B");
        ReachedHolding indirect = reached(true, "A", "B", "C");

        assertThat(BriefingText.reachSplit(List.of(direct))).isEqualTo("1 Direct");
        assertThat(BriefingText.reachSplit(List.of(direct, indirect, indirect))).isEqualTo("1 Direct, 2 Indirect");
        assertThat(BriefingText.reachedCount(1)).isEqualTo("1 Holding Reached");
        assertThat(BriefingText.reachedCount(3)).isEqualTo("3 Holdings Reached");
    }

    private static ReachedHolding reached(boolean indirect, String supplier, String middle, String customer) {
        return new ReachedHolding(1L, "NVDA", null, indirect, supplier, middle, customer, null, null, null, null);
    }

    /** The labels the website has for a kind of thing, read from format.ts so the two can never drift apart. */
    private static Map<String, String> labelsIn(String constant) throws IOException {
        String source = Files.readString(Path.of("..", "frontend", "src", "format.ts"), StandardCharsets.UTF_8);
        int start = source.indexOf("export const " + constant);
        assertThat(start).as(constant + " in format.ts").isGreaterThanOrEqualTo(0);
        String block = source.substring(start, source.indexOf("}", start));
        Map<String, String> labels = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("([A-Z_]+):\\s*'([^']+)'").matcher(block);
        while (entry.find()) {
            labels.put(entry.group(1), entry.group(2));
        }
        return labels;
    }
}
