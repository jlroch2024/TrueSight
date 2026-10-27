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
        // This answer was saved before common names and tickers were asked for, so it has neither.
        assertThat(found.getFirst()).isEqualTo(new GeminiAnswer.Found(
                "Taiwan Semiconductor Manufacturing Company Limited", "TSMC", "", "", RelationshipType.SUPPLIER,
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

    // ---- common names and tickers (TS-180) -------------------------------------------------------------------------

    @Test
    void readsTheCommonNameAndTickerGeminiGaveInItsRealAmdAnswer() {
        List<GeminiAnswer.Found> found = answers.read(Examples.geminiAmd());

        assertThat(found).isNotEmpty().allSatisfy(item -> {
            assertThat(item.company()).isNotBlank();
            assertThat(item.quote()).isNotBlank();
        });
        // AMD's 10-K names a customer "OpenAI OpCo, LLC", which people know as OpenAI.
        assertThat(found).anySatisfy(item -> {
            assertThat(item.company()).startsWith("OpenAI OpCo");
            assertThat(item.commonName()).isEqualTo("OpenAI");
        });
        // A supplier whose common name drops the report's wording, and whose stock is listed.
        assertThat(found).anySatisfy(item -> {
            assertThat(item.company()).startsWith("Taiwan Semiconductor Manufacturing");
            assertThat(item.commonName()).isEqualTo("TSMC");
            assertThat(item.ticker()).isEqualTo("TSM");
        });
        assertThat(found).anySatisfy(item -> {
            assertThat(item.company()).isEqualTo("Samsung Electronics Co., Ltd.");
            assertThat(item.commonName()).isEqualTo("Samsung Electronics");
        });
    }

    @Test
    void answersSavedBeforeCommonNamesWereAskedForReadAsHavingNone() {
        assertThat(answers.read(Examples.geminiNvidia()))
                .allSatisfy(item -> assertThat(item.commonName()).isEmpty())
                .allSatisfy(item -> assertThat(item.ticker()).isEmpty());
    }

    @Test
    void readsTheCommonNamesPassAnswerForTheStoredNames() {
        List<GeminiAnswer.CommonNameFound> found = answers.readCommonNames(Examples.geminiCommonNames());

        assertThat(found).isNotEmpty().allSatisfy(item -> assertThat(item.name()).isNotBlank());
        assertThat(found).anySatisfy(item -> {
            assertThat(item.name()).startsWith("OpenAI OpCo");
            assertThat(item.commonName()).isEqualTo("OpenAI");
        });
        assertThat(found).anySatisfy(item -> {
            assertThat(item.name()).startsWith("Taiwan Semiconductor Manufacturing");
            assertThat(item.commonName()).isEqualTo("TSMC");
            assertThat(item.ticker()).isEqualTo("TSM");
        });
        assertThat(found).anySatisfy(item -> {
            assertThat(item.name()).startsWith("Hon Hai Precision");
            assertThat(item.commonName()).isEqualTo("Foxconn");
        });
    }

    @Test
    void theCommonNamesPassAnswersForEveryStoredNameTheSavedRelationshipAnswersFound() {
        List<String> storedNames = java.util.stream.Stream.concat(
                        answers.read(Examples.geminiNvidia()).stream(), answers.read(Examples.geminiAmd()).stream())
                .map(GeminiAnswer.Found::company).distinct().toList();

        assertThat(answers.readCommonNames(Examples.geminiCommonNames()))
                .extracting(GeminiAnswer.CommonNameFound::name).containsAll(storedNames);
    }

    @Test
    void aCommonNamesAnswerThatIsNotAJsonListIsUnreadable() {
        assertThatThrownBy(() -> answers.readCommonNames(null)).isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
        assertThatThrownBy(() -> answers.readCommonNames("<html>Bad gateway</html>"))
                .isInstanceOf(GeminiAnswer.UnreadableAnswerException.class);
    }
}
