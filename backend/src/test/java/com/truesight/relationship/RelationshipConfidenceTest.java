package com.truesight.relationship;

import com.truesight.relationship.RelationshipConfidence.Effect;
import com.truesight.relationship.RelationshipConfidence.Level;
import com.truesight.relationship.RelationshipConfidence.Reason;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rule of {@link RelationshipConfidence}, as the See How Confident TrueSight Is story agreed it. The evidence is
 * NVIDIA's real sentence about TSMC. The doubt reason describes the kind of sentence the second check is agreed to
 * doubt (a plan, not a current supplier); it is not a saved AI answer, as no saved answer doubts.
 */
class RelationshipConfidenceTest {

    private static final String DOUBT = "The sentence only describes a plan to work together.";

    @Test
    void anApprovedRelationshipIsHighBecauseAnAnalystApprovedIt() {
        Relationship approved = relationship(ReviewStatus.APPROVED, null, null, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(approved, false);

        assertThat(confidence.level()).isEqualTo(Level.HIGH);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("An Analyst Approved It", Effect.RAISES),
                new Reason("Quote Match Not Recorded", Effect.NONE),
                new Reason("Not Yet Second-Checked", Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void approvalOutweighsASecondCheckDoubt() {
        Relationship approved = relationship(ReviewStatus.APPROVED, "0.900", SecondCheckVerdict.DOUBTS, DOUBT);

        RelationshipConfidence confidence = RelationshipConfidence.of(approved, false);

        assertThat(confidence.level()).isEqualTo(Level.HIGH);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("An Analyst Approved It", Effect.RAISES),
                new Reason("Partial Quote Match (90%)", Effect.NONE),
                new Reason("Second Check Doubts: " + DOUBT, Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void anUnreviewedRelationshipWithAStrongMatchAndAnAgreeingSecondCheckIsHigh() {
        Relationship strong = relationship(ReviewStatus.UNREVIEWED, "1.000", SecondCheckVerdict.AGREES, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(strong, false);

        assertThat(confidence.level()).isEqualTo(Level.HIGH);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("Not Yet Reviewed", Effect.NONE),
                new Reason("Strong Quote Match (100%)", Effect.RAISES),
                new Reason("Second Check Agrees", Effect.RAISES),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void aMatchOfExactly95PercentIsStrong() {
        Relationship strong = relationship(ReviewStatus.UNREVIEWED, "0.950", SecondCheckVerdict.AGREES, null);

        assertThat(RelationshipConfidence.of(strong, false).level()).isEqualTo(Level.HIGH);
        assertThat(RelationshipConfidence.of(strong, false).reasons()).contains(
                new Reason("Strong Quote Match (95%)", Effect.RAISES));
    }

    @Test
    void anUnreviewedRelationshipTheSecondCheckDoubtsIsLowWithTheDoubtsReason() {
        Relationship doubted = relationship(ReviewStatus.UNREVIEWED, "1.000", SecondCheckVerdict.DOUBTS, DOUBT);

        RelationshipConfidence confidence = RelationshipConfidence.of(doubted, false);

        assertThat(confidence.level()).isEqualTo(Level.LOW);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("Not Yet Reviewed", Effect.NONE),
                new Reason("Strong Quote Match (100%)", Effect.NONE),
                new Reason("Second Check Doubts: " + DOUBT, Effect.LOWERS),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void aDoubtWithNoReasonSaysOnlyThatItDoubts() {
        Relationship doubted = relationship(ReviewStatus.UNREVIEWED, null, SecondCheckVerdict.DOUBTS, null);

        assertThat(RelationshipConfidence.of(doubted, false).reasons()).contains(
                new Reason("Second Check Doubts", Effect.LOWERS));
    }

    @Test
    void aPartialMatchWithAnAgreeingSecondCheckIsMedium() {
        // 22 of the TSMC sentence's 24 words, as QuoteCheckerTest finds; recorded rounded down to 0.916.
        Relationship partial = relationship(ReviewStatus.UNREVIEWED, "0.916", SecondCheckVerdict.AGREES, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(partial, false);

        assertThat(confidence.level()).isEqualTo(Level.MEDIUM);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("Not Yet Reviewed", Effect.NONE),
                new Reason("Partial Quote Match (91%)", Effect.NONE),
                new Reason("Second Check Agrees", Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void aStrongMatchNotYetSecondCheckedIsMedium() {
        Relationship unchecked = relationship(ReviewStatus.UNREVIEWED, "1.000", null, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(unchecked, false);

        assertThat(confidence.level()).isEqualTo(Level.MEDIUM);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("Not Yet Reviewed", Effect.NONE),
                new Reason("Strong Quote Match (100%)", Effect.NONE),
                new Reason("Not Yet Second-Checked", Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void aShareJustUnder95PercentReadsAs94NotAs95() {
        Relationship justUnder = relationship(ReviewStatus.UNREVIEWED, "0.949", SecondCheckVerdict.AGREES, null);

        assertThat(RelationshipConfidence.of(justUnder, false).level()).isEqualTo(Level.MEDIUM);
        assertThat(RelationshipConfidence.of(justUnder, false).reasons()).contains(
                new Reason("Partial Quote Match (94%)", Effect.NONE));
    }

    /** Found before the share was recorded: judged on the review and the second check alone. */
    @Test
    void aRelationshipFoundBeforeThisStoryShowsQuoteMatchNotRecordedAndIsJudgedOnTheOtherTwo() {
        Relationship agreed = relationship(ReviewStatus.UNREVIEWED, null, SecondCheckVerdict.AGREES, null);
        Relationship doubted = relationship(ReviewStatus.UNREVIEWED, null, SecondCheckVerdict.DOUBTS, DOUBT);
        Relationship approved = relationship(ReviewStatus.APPROVED, null, SecondCheckVerdict.DOUBTS, DOUBT);

        assertThat(RelationshipConfidence.of(agreed, false).level()).isEqualTo(Level.MEDIUM);
        assertThat(RelationshipConfidence.of(agreed, false).reasons()).contains(
                new Reason("Quote Match Not Recorded", Effect.NONE));
        assertThat(RelationshipConfidence.of(doubted, false).level()).isEqualTo(Level.LOW);
        assertThat(RelationshipConfidence.of(approved, false).level()).isEqualTo(Level.HIGH);
    }

    /** An alert made before the rejection still lists the path, so a rejected link must never read as confident. */
    @Test
    void aRejectedRelationshipIsLowEvenWithAStrongMatchAndAnAgreeingSecondCheck() {
        Relationship rejected = relationship(ReviewStatus.REJECTED, "1.000", SecondCheckVerdict.AGREES, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(rejected, false);

        assertThat(confidence.level()).isEqualTo(Level.LOW);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("An Analyst Rejected It", Effect.LOWERS),
                new Reason("Strong Quote Match (100%)", Effect.NONE),
                new Reason("Second Check Agrees", Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void aRejectionIsTheReasonThatLowersItWhenTheSecondCheckDoubtsToo() {
        Relationship rejected = relationship(ReviewStatus.REJECTED, null, SecondCheckVerdict.DOUBTS, DOUBT);

        RelationshipConfidence confidence = RelationshipConfidence.of(rejected, false);

        assertThat(confidence.level()).isEqualTo(Level.LOW);
        assertThat(confidence.reasons()).containsExactly(
                new Reason("An Analyst Rejected It", Effect.LOWERS),
                new Reason("Quote Match Not Recorded", Effect.NONE),
                new Reason("Second Check Doubts: " + DOUBT, Effect.NONE),
                new Reason("Only One Report Names It", Effect.NONE));
    }

    /** The sample the website's tests use is in the API's exact shape, so its confidences follow the same rule. */
    @Test
    void theSampleRelationshipsCarryTheConfidenceTheRuleGives() {
        JsonNode sample = JsonMapper.builder().build().readTree(Examples.relationships());
        for (JsonNode found : sample) {
            RelationshipConfidence expected = RelationshipConfidence.of(
                    relationship(ReviewStatus.valueOf(found.path("reviewStatus").asText()), null, null, null), false);
            JsonNode confidence = found.path("confidence");
            assertThat(confidence.path("level").asText()).isEqualTo(expected.level().name());
            assertThat(confidence.path("reasons")).hasSize(expected.reasons().size());
            for (int i = 0; i < expected.reasons().size(); i++) {
                assertThat(confidence.path("reasons").get(i).path("text").asText())
                        .isEqualTo(expected.reasons().get(i).text());
                assertThat(confidence.path("reasons").get(i).path("effect").asText())
                        .isEqualTo(expected.reasons().get(i).effect().name());
            }
        }
    }

    // ---- Both Reports Name It ------------------------------------------------------------------------------------

    @Test
    void bothReportsNamingItRaisesLowToMediumAndMediumToHigh() {
        Relationship doubted = relationship(ReviewStatus.UNREVIEWED, "1.000", SecondCheckVerdict.DOUBTS, DOUBT);
        Relationship unchecked = relationship(ReviewStatus.UNREVIEWED, "1.000", null, null);

        assertThat(RelationshipConfidence.of(doubted, false).level()).isEqualTo(Level.LOW);
        assertThat(RelationshipConfidence.of(doubted, true).level()).isEqualTo(Level.MEDIUM);
        assertThat(RelationshipConfidence.of(unchecked, false).level()).isEqualTo(Level.MEDIUM);
        RelationshipConfidence raised = RelationshipConfidence.of(unchecked, true);
        assertThat(raised.level()).isEqualTo(Level.HIGH);
        assertThat(raised.reasons()).contains(new Reason("Both Reports Name It", Effect.RAISES));
    }

    @Test
    void aRelationshipOnlyOneReportNamesKeepsItsLevelAndSaysSo() {
        Relationship unchecked = relationship(ReviewStatus.UNREVIEWED, "1.000", null, null);

        RelationshipConfidence confidence = RelationshipConfidence.of(unchecked, false);

        assertThat(confidence.level()).isEqualTo(Level.MEDIUM);
        assertThat(confidence.reasons()).contains(new Reason("Only One Report Names It", Effect.NONE));
    }

    @Test
    void approvedIsHighAndRejectedIsLowWhateverTheOtherReportSays() {
        Relationship approved = relationship(ReviewStatus.APPROVED, "1.000", null, null);
        Relationship rejected = relationship(ReviewStatus.REJECTED, "1.000", null, null);

        assertThat(RelationshipConfidence.of(approved, true).level()).isEqualTo(Level.HIGH);
        assertThat(RelationshipConfidence.of(rejected, true).level()).isEqualTo(Level.LOW);
        assertThat(RelationshipConfidence.of(rejected, true).reasons())
                .contains(new Reason("Both Reports Name It", Effect.NONE));
    }

    private static Relationship relationship(ReviewStatus review, String quoteMatch, SecondCheckVerdict secondCheck,
                                             String secondCheckReason) {
        Relationship relationship = new Relationship(1L, 1L, 2L, RelationshipType.SUPPLIER, "semiconductor wafers",
                "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung "
                        + "Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.");
        relationship.setReviewStatus(review);
        relationship.setQuoteMatch(quoteMatch == null ? null : new BigDecimal(quoteMatch));
        relationship.setSecondCheck(secondCheck);
        relationship.setSecondCheckReason(secondCheckReason);
        return relationship;
    }
}
