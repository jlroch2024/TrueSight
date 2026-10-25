package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiAnswerTest {

    private final GeminiAnswer answers = new GeminiAnswer(JsonMapper.builder().build());

    @Test
    void readsEveryRelationshipInGeminisRealAnswer() {
        List<GeminiAnswer.Found> found = answers.read(Examples.geminiNvidia());

        assertThat(found).hasSize(7).allMatch(f -> f.relationship() == RelationshipType.SUPPLIER);
        assertThat(found.getFirst()).isEqualTo(new GeminiAnswer.Found(
                "Taiwan Semiconductor Manufacturing Company Limited", "TSMC", RelationshipType.SUPPLIER,
                "semiconductor wafers",
                "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and "
                        + "Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.", null, ""));
        assertThat(found).extracting(GeminiAnswer.Found::company).contains("SK Hynix Inc.", "Fabrinet");
    }

    @Test
    void anAnswerCutOffPartWayIsNotValidJson() {
        // The real answer with the end of its list cut off, as when Gemini stops early.
        String cutOff = Examples.geminiNvidia().replace("final products.\\\"}]\"", "final products.\\\"}\"");

        assertThat(cutOff).isNotEqualTo(Examples.geminiNvidia());
        assertThatThrownBy(() -> answers.read(cutOff)).isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
    }

    @Test
    void anEmptyOrUnreadableResponseIsNotValidJson() {
        assertThatThrownBy(() -> answers.read(null)).isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
        assertThatThrownBy(() -> answers.read("<html>Bad gateway</html>"))
                .isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
        assertThatThrownBy(() -> answers.read("{\"candidates\": []}"))
                .isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
    }

    @Test
    void anItemWithARelationshipOtherThanSupplierOrCustomerIsLeftOut() {
        // The real answer with TSMC's relationship changed to one that was not asked for.
        String changed = Examples.geminiNvidia().replaceFirst("SUPPLIER", "PARTNER");

        assertThat(answers.read(changed)).hasSize(6)
                .extracting(GeminiAnswer.Found::company)
                .doesNotContain("Taiwan Semiconductor Manufacturing Company Limited");
    }

    @Test
    void readsTheRealNvidiaAnswerAndLeavesEveryRelationshipUnclassified() {
        assertThat(answers.readCriticalities(Examples.geminiNvidiaCriticality())).isEmpty();
        assertThat(answers.read(Examples.geminiNvidiaCriticality()))
                .extracting(GeminiAnswer.Found::criticality)
                .containsOnlyNulls();
        assertThat(answers.read(Examples.geminiNvidiaCriticality()))
                .extracting(GeminiAnswer.Found::criticalityQuote)
                .containsOnly("");
    }

    /** Wraps the text of an answer the way Gemini's response carries it. */
    private static String responseWith(String text) throws Exception {
        return JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @Test
    void readsEachVerdictOfTheSecondCheckWithItsReason() throws Exception {
        String answer = responseWith("[{\"index\":1,\"verdict\":\"AGREES\",\"reason\":\"A current supplier.\"},"
                + "{\"index\":2,\"verdict\":\"DOUBTS\",\"reason\":\"Only a plan.\"}]");

        assertThat(answers.readSecondChecks(answer)).containsExactly(
                new GeminiAnswer.SecondCheckFound(1, SecondCheckVerdict.AGREES, "A current supplier."),
                new GeminiAnswer.SecondCheckFound(2, SecondCheckVerdict.DOUBTS, "Only a plan."));
    }

    @Test
    void aSecondCheckItemWithNoIndexOrAnUnknownVerdictIsLeftOut() throws Exception {
        String answer = responseWith("[{\"verdict\":\"AGREES\",\"reason\":\"No index.\"},"
                + "{\"index\":2,\"verdict\":\"MAYBE\",\"reason\":\"Not asked for.\"},"
                + "{\"index\":\"3\",\"verdict\":\"AGREES\",\"reason\":\"Index is text.\"},"
                + "{\"index\":4,\"verdict\":\"doubts\",\"reason\":\"Lower case is fine.\"}]");

        assertThat(answers.readSecondChecks(answer)).containsExactly(
                new GeminiAnswer.SecondCheckFound(4, SecondCheckVerdict.DOUBTS, "Lower case is fine."));
    }

    @Test
    void aSecondCheckAnswerThatIsNotAJsonListIsUnreadable() throws Exception {
        assertThatThrownBy(() -> answers.readSecondChecks(null)).isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
        assertThatThrownBy(() -> answers.readSecondChecks(responseWith("[{\"index\":1,\"verdict\":\"AGREES\"")))
                .isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
        assertThatThrownBy(() -> answers.readSecondChecks(responseWith("{\"index\":1}")))
                .isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
    }
}
