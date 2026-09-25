package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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
                        + "Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers."));
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
}
