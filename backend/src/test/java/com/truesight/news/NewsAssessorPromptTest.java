package com.truesight.news;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filter Out Noise's prompt, on headlines labelled by hand, with Gemini's real answers saved in
 * {@code docs/examples/news-filter-judge.json}. Nothing here calls Gemini: {@code measure_news_filter.py} does, by hand.
 */
class NewsAssessorPromptTest {

    private static final String SENTENCE = "An event at another company is noise unless the headline says it affects the "
            + "named company or its supply. A past event, or one that was called off or blocked, is noise.";

    @Test
    void thePromptHasTheSentenceWordForWordJustBeforeUseOnlyTheHeadline() {
        assertThat(NewsAssessor.PROMPT).contains(SENTENCE + "\nUse only the headline.");
    }

    @Test
    void thePromptIsTheOneTheSavedAnswersMeasuredSoChangingItMeansMeasuringAgain() throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(NewsAssessor.PROMPT.getBytes(StandardCharsets.UTF_8)));

        assertThat(sha).isEqualTo(Examples.newsFilterJudge().path("promptSha256").stringValue());
    }

    @Test
    void aTsmcAccidentTaggedNvidiaIsNoiseAndTheSameAccidentTaggedTsmcIsAnEvent() {
        assertThat(everyAnswerIsAnEvent("NVIDIA", "Four dead after collapse at TSMC Kaohsiung site")).isFalse();
        assertThat(everyAnswerIsAnEvent("TSMC", "Four dead after collapse at TSMC Kaohsiung site")).isTrue();
    }

    @Test
    void thePromptSaysAnEventMustBeAbleToStopOrDelayMakingBuyingShippingOrSellingGoods() {
        assertThat(NewsAssessor.PROMPT)
                .contains("could stop or delay the named company making, buying, shipping or selling goods")
                .contains("If it could not do that, it is\nnoise");
    }

    @Test
    void thePromptNamesLeadershipChangesLawsuitsConsumerAccidentsAndRegulatorCommentAsNoise() {
        assertThat(NewsAssessor.PROMPT)
                .contains("a leadership or board change")
                .contains("a lawsuit, settlement, patent fight or other dispute about money or intellectual property")
                .contains("one consumer's accident, or an investigation into a single product incident")
                .contains("a regulator's comment, opinion or routine review")
                .contains("unless the headline itself says production, supply or sales are affected");
    }

    @Test
    void theReasonIsTheAisOwnReadingOfWhyItMattersAndNeverTalksAboutTheHeadline() {
        // Why It Matters is the AI's interpretation, not the facts again (What Happened gives those); and the alert already
        // says it rests on the headline alone, so no reason mentions it.
        assertThat(NewsAssessor.PROMPT)
                .contains("your own reading of why this matters")
                .contains("Do not repeat what happened")
                .contains("never start with or mention \"the headline\".");
    }

    @Test
    void thePromptStillAsksForEveryFieldTheReaderNeeds() {
        assertThat(NewsAssessor.PROMPT)
                .contains("- index:")
                .contains("- isEvent:")
                .contains("- eventType:")
                .contains("- severity:")
                .contains("- confidence:")
                .contains("- summary:")
                .contains("- reason:");
    }

    @Test
    void theSetHoldsTheSixtyLabelledHeadlinesAndTwentyNewOnesWithAnAnswerForEachOrder() {
        JsonNode cases = Examples.newsFilterJudge().path("cases");

        assertThat(cases).hasSize(80);
        assertThat(cases).filteredOn(item -> item.path("set").stringValue().equals("new")).hasSize(20);
        assertThat(cases).allSatisfy(item -> assertThat(item.path("answer")).hasSize(3));
    }

    /** True when all three orders call this tagged headline an event, false when none does. */
    private static boolean everyAnswerIsAnEvent(String company, String headlineStart) {
        for (JsonNode item : Examples.newsFilterJudge().path("cases")) {
            if (item.path("company").stringValue().equals(company)
                    && item.path("headline").stringValue().startsWith(headlineStart)) {
                boolean first = item.path("answer").get(0).path("isEvent").asBoolean();
                for (JsonNode answer : item.path("answer")) {
                    assertThat(answer.path("isEvent").asBoolean()).isEqualTo(first);
                }
                return first;
            }
        }
        throw new AssertionError("No saved answer for " + company + ": " + headlineStart);
    }
}
