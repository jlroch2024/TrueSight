package com.truesight.news;

import com.truesight.news.NewsAssessmentReader.Judgement;
import com.truesight.news.NewsAssessmentReader.UnreadableAnswerException;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading Gemini's judgement of the headlines. The real answer is {@link Examples#geminiNewsAssessment()}; the broken
 * ones are made by hand, wrapped the way Gemini wraps its answers, to check each guardrail.
 */
class NewsAssessmentReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NewsAssessmentReader reader = new NewsAssessmentReader();

    /** A whole Gemini API response whose answer text is {@code answer}, as in the saved example. */
    private static String gemini(String answer) {
        return JSON.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("role", "model", "parts", List.of(Map.of("text", answer)))))));
    }

    /** One answer item, as JSON text. */
    private static String item(int index, boolean isEvent, String eventType, String severity, String confidence,
                               String summary, String reason) {
        return JSON.writeValueAsString(Map.of("index", index, "isEvent", isEvent, "eventType", eventType,
                "severity", severity, "confidence", confidence, "summary", summary, "reason", reason));
    }

    private static String assessments(String... items) {
        return "{\"assessments\": [" + String.join(",", items) + "]}";
    }

    private static final String FIRE = item(1, true, "PRODUCTION_STOP", "HIGH", "MEDIUM",
            "A fire stopped production at a plant.", "A plant stoppage disrupts making goods.");

    @Test
    void theSavedRealAnswerGivesEveryOneOfTheTwentyHeadlinesTheJudgementInIt() {
        Map<Integer, Judgement> judgements = reader.read(Examples.geminiNewsAssessment(), 20);

        JsonNode saved = JSON.readTree(JSON.readTree(Examples.geminiNewsAssessment())
                .path("candidates").path(0).path("content").path("parts").path(0).path("text").asString());
        assertThat(judgements).hasSize(20);
        for (JsonNode answer : saved) {
            Judgement judgement = judgements.get(answer.path("index").asInt());
            assertThat(judgement.event()).isEqualTo(answer.path("isEvent").asBoolean());
            assertThat(judgement.confidence().name()).isEqualTo(answer.path("confidence").asString());
            assertThat(judgement.reason()).isEqualTo(answer.path("reason").asString());
            // Every one of the 20 is noise, so it has no type, severity or summary.
            assertThat(judgement.eventType()).isNull();
            assertThat(judgement.severity()).isNull();
            assertThat(judgement.summary()).isNull();
        }
        assertThat(judgements.get(3).reason())
                .isEqualTo("This reports financial performance expectations and profit records rather than a supply chain issue.");
    }

    @Test
    void anEventKeepsItsTypeSeverityConfidenceSummaryAndReason() {
        Map<Integer, Judgement> judgements = reader.read(gemini(assessments(FIRE)), 1);

        assertThat(judgements.get(1)).isEqualTo(new Judgement(true, EventType.PRODUCTION_STOP, Severity.HIGH,
                Confidence.MEDIUM, "A fire stopped production at a plant.", "A plant stoppage disrupts making goods."));
    }

    @Test
    void noiseKeepsItsReasonAndConfidenceButNoTypeOrSeverity() {
        String noise = item(1, false, "SUPPLY_SHORTAGE", "LOW", "HIGH", "Ignored.", "Quarterly earnings, not a disruption.");

        Judgement judgement = reader.read(gemini(assessments(noise)), 1).get(1);

        assertThat(judgement).isEqualTo(new Judgement(false, null, null, Confidence.HIGH, null,
                "Quarterly earnings, not a disruption."));
    }

    @Test
    void anAnswerNamingAnUnknownEventTypeIsStoredAsNoiseThatCouldNotBeUsed() {
        String unknownType = item(1, true, "ALIEN_INVASION", "HIGH", "HIGH", "Something happened.", "A reason.");

        Judgement judgement = reader.read(gemini(assessments(unknownType)), 1).get(1);

        assertThat(judgement).isEqualTo(new Judgement(false, null, null, Confidence.LOW, null,
                "The AI's answer could not be used."));
    }

    @Test
    void anUnknownSeverityConfidenceOrNoReasonIsAlsoNoiseThatCouldNotBeUsed() {
        String unknownSeverity = item(1, true, "PRODUCTION_STOP", "CATASTROPHIC", "HIGH", "A fire.", "A reason.");
        String unknownConfidence = item(2, false, "", "", "CERTAIN", "", "Quarterly earnings, not a disruption.");
        String noReason = item(3, false, "", "", "HIGH", "", " ");
        String eventNotTrueOrFalse = "{\"index\": 4, \"isEvent\": \"yes\", \"eventType\": \"PRODUCTION_STOP\","
                + " \"severity\": \"HIGH\", \"confidence\": \"HIGH\", \"summary\": \"A fire.\", \"reason\": \"A reason.\"}";

        Map<Integer, Judgement> judgements = reader.read(
                gemini(assessments(unknownSeverity, unknownConfidence, noReason, eventNotTrueOrFalse)), 4);

        assertThat(judgements).hasSize(4);
        assertThat(judgements.values()).allSatisfy(judgement -> {
            assertThat(judgement.event()).isFalse();
            assertThat(judgement.reason()).isEqualTo("The AI's answer could not be used.");
        });
    }

    @Test
    void aHeadlineMissingFromTheAnswerIsLeftUnassessed() {
        String first = item(1, false, "", "", "HIGH", "", "Share price commentary.");
        String third = item(3, false, "", "", "HIGH", "", "Stock advice.");

        Map<Integer, Judgement> judgements = reader.read(gemini(assessments(first, third)), 3);

        assertThat(judgements).containsOnlyKeys(1, 3);
    }

    @Test
    void anItemForAHeadlineThatWasNotSentOrIsAnsweredTwiceIsIgnored() {
        String noIndex = "{\"isEvent\": false, \"confidence\": \"HIGH\", \"reason\": \"No index.\"}";
        String notSent = item(9, false, "", "", "HIGH", "", "Not one of the headlines.");
        String again = item(1, false, "", "", "HIGH", "", "The second answer for headline 1.");

        Map<Integer, Judgement> judgements = reader.read(gemini(assessments(FIRE, noIndex, notSent, again)), 2);

        assertThat(judgements).containsOnlyKeys(1);
        assertThat(judgements.get(1).event()).isTrue();
    }

    @Test
    void valuesInLowerCaseAreUnderstood() {
        String lower = item(1, true, "labour_action", "medium", "low", "Workers went on strike.", "A strike stops work.");

        Judgement judgement = reader.read(gemini(assessments(lower)), 1).get(1);

        assertThat(judgement.eventType()).isEqualTo(EventType.LABOUR_ACTION);
        assertThat(judgement.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(judgement.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void anAnswerThatIsNotTheJsonAskedForCannotBeRead() {
        assertThatThrownBy(() -> reader.read(gemini("Sorry, I cannot help with that."), 1))
                .isInstanceOf(UnreadableAnswerException.class);
        assertThatThrownBy(() -> reader.read(gemini("{\"answers\": []}"), 1))
                .isInstanceOf(UnreadableAnswerException.class);
        assertThatThrownBy(() -> reader.read("", 1)).isInstanceOf(UnreadableAnswerException.class);
        assertThatThrownBy(() -> reader.read("{\"candidates\": []}", 1)).isInstanceOf(UnreadableAnswerException.class);
    }
}
