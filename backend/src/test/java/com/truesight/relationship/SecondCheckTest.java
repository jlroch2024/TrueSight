package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The second check's judge, on claims labelled by hand from real filings, with Gemini's real readings saved in
 * {@code docs/examples/second-check-judge.json}. Nothing here calls Gemini: {@code measure_second_check.py} does, by hand.
 */
class SecondCheckTest {

    @Test
    void thePromptIsTheOneTheSavedReadingsMeasuredSoChangingItMeansMeasuringAgain() throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(SecondCheck.PROMPT.getBytes(StandardCharsets.UTF_8)));

        assertThat(sha).isEqualTo(Examples.secondCheckJudge().path("promptSha256").stringValue());
    }

    @Test
    void theJudgeGetsEveryLabelledClaimRight() {
        List<String> wrong = new ArrayList<>();
        int scored = 0;
        for (JsonNode labelled : Examples.secondCheckJudge().path("cases")) {
            if (labelled.path("label").isNull()) {
                continue; // a judgement call, shown in the README but not scored
            }
            scored++;
            if (accepted(labelled) != labelled.path("label").booleanValue()) {
                wrong.add(labelled.path("writer").stringValue() + " " + labelled.path("type").stringValue() + " "
                        + labelled.path("other").stringValue());
            }
        }
        assertThat(scored).isEqualTo(60);
        assertThat(wrong).isEmpty();
    }

    @Test
    void klasCustomerTableReadsAsACustomerOfKlaThoughTheSentenceNeverNamesKla() {
        JsonNode kla = Examples.secondCheckJudge().path("cases").values().stream()
                .filter(labelled -> labelled.path("writer").stringValue().equals("KLA Corp")
                        && labelled.path("label").booleanValue())
                .findFirst().orElseThrow();

        assertThat(kla.path("sentence").stringValue()).doesNotContain("KLA");
        assertThat(kla.path("answer").path("reading").stringValue()).isEqualTo("BUYS_FROM_THE_WRITER");
        assertThat(accepted(kla)).isTrue();
    }

    @Test
    void aReversedClaimIsDoubtedThoughTheSameCompaniesAreNamed() {
        assertThat(SecondCheckReading.SUPPLIES_THE_WRITER.verdictOn(RelationshipType.SUPPLIER))
                .isEqualTo(SecondCheckVerdict.AGREES);
        assertThat(SecondCheckReading.SUPPLIES_THE_WRITER.verdictOn(RelationshipType.CUSTOMER))
                .isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.BUYS_FROM_THE_WRITER.verdictOn(RelationshipType.CUSTOMER))
                .isEqualTo(SecondCheckVerdict.AGREES);
        assertThat(SecondCheckReading.BUYS_FROM_THE_WRITER.verdictOn(RelationshipType.SUPPLIER))
                .isEqualTo(SecondCheckVerdict.DOUBTS);
    }

    @Test
    void aCompanyThatBothSuppliesAndBuysAgreesWithEitherClaim() {
        assertThat(SecondCheckReading.BOTH.verdictOn(RelationshipType.SUPPLIER)).isEqualTo(SecondCheckVerdict.AGREES);
        assertThat(SecondCheckReading.BOTH.verdictOn(RelationshipType.CUSTOMER)).isEqualTo(SecondCheckVerdict.AGREES);
    }

    @Test
    void everyOtherReadingIsADoubtThatSaysWhatKind() {
        assertThat(SecondCheckReading.COMPETITOR.verdictOn(RelationshipType.SUPPLIER)).isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.ENDED.verdictOn(RelationshipType.CUSTOMER)).isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.PLANNED.verdictOn(RelationshipType.SUPPLIER)).isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.OTHER_TIE.verdictOn(RelationshipType.CUSTOMER)).isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.NOT_STATED.verdictOn(RelationshipType.SUPPLIER)).isEqualTo(SecondCheckVerdict.DOUBTS);
        assertThat(SecondCheckReading.COMPETITOR.reasonOn(RelationshipType.SUPPLIER, "Named as one."))
                .isEqualTo("Reads as a Competitor. Named as one.");
        assertThat(SecondCheckReading.SUPPLIES_THE_WRITER.reasonOn(RelationshipType.SUPPLIER, "It supplies them."))
                .isEqualTo("It supplies them.");
    }

    @Test
    void thePassageHoldsTheEvidenceAndTheTextAroundItCutAtWholeWordsOnOneLine() {
        String before = "word ".repeat(400);
        String after = " next".repeat(200);
        String passage = SecondCheck.passage(before + "We buy\n  wafers." + after, "We buy\n  wafers.");

        assertThat(passage).contains("We buy wafers.").startsWith("word ").endsWith(" next").doesNotContain("\n");
        assertThat(passage.length()).isLessThanOrEqualTo(
                SecondCheck.CHARACTERS_BEFORE + "We buy wafers.".length() + SecondCheck.CHARACTERS_AFTER);
    }

    @Test
    void theEvidenceAloneIsThePassageWhenTheReportNoLongerHoldsIt() {
        assertThat(SecondCheck.passage("Some other text.", "We buy  wafers.")).isEqualTo("We buy wafers.");
    }

    @Test
    void everySavedReadingIsOneTheJudgeCanBeGiven() {
        List<String> unknown = new ArrayList<>();
        for (JsonNode labelled : Examples.secondCheckJudge().path("cases")) {
            String reading = labelled.path("answer").path("reading").stringValue();
            try {
                SecondCheckReading.valueOf(reading);
            } catch (IllegalArgumentException exception) {
                unknown.add(reading);
            }
        }
        assertThat(unknown).isEmpty();
    }

    private static boolean accepted(JsonNode labelled) {
        SecondCheckReading reading = SecondCheckReading.valueOf(labelled.path("answer").path("reading").stringValue());
        return reading.verdictOn(RelationshipType.valueOf(labelled.path("type").stringValue()))
                == SecondCheckVerdict.AGREES;
    }
}
