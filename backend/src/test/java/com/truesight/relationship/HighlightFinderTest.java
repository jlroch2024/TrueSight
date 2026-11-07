package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checking the AI's highlights. The first test uses Gemini's real answer in {@code docs/examples/}. That answer copied
 * every phrase correctly, so the tests of what is dropped use small answers written by hand here: see {@link #handWritten}.
 * Every sentence is real, from the saved 10-Ks.
 */
class HighlightFinderTest {

    private static final Long CALL_ID = 5L;

    /** From NVIDIA's 10-K in docs/examples. */
    private static final String MEMORY = "We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.";

    /** From NVIDIA's 10-K in docs/examples. */
    private static final String SHARE_OF_REVENUE = "For fiscal year 2024, sales to one direct customer represented 13% of "
            + "total revenue, and were primarily attributable to the Compute & Networking segment.";

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AiCallLog calls = mock(AiCallLog.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final HighlightFinder finder = new HighlightFinder(gemini, new GeminiAnswer(json), calls);

    @Test
    void marksEachPhraseOfGeminisRealAnswerWhereItIsInItsQuote() {
        List<String> quotes = Examples.highlightQuotes();
        assertThat(quotes.subList(0, 2)).allMatch(Examples.nvidia10K()::contains);
        assertThat(quotes.subList(2, 5)).allMatch(Examples.amd10K()::contains);
        List<Relationship> batch = new ArrayList<>();
        for (int i = 0; i < quotes.size(); i++) {
            batch.add(relationship(i + 1L, quotes.get(i)));
        }
        when(gemini.generateHighlights(any())).thenReturn(new AiAnswer(Examples.geminiHighlights(), CALL_ID));

        Map<Long, List<RelationshipHighlight>> found = finder.find(batch);

        // "Samsung" is also inside the longer name, so it takes its own place after it. So does "OpenAI".
        assertThat(marked(quotes.get(0), found.get(1L))).containsExactly(
                "COMPANY Taiwan Semiconductor Manufacturing Company Limited", "COMPANY TSMC",
                "COMPANY Samsung Electronics Co., Ltd.", "COMPANY Samsung", "SUPPLIED semiconductor wafers");
        assertThat(marked(quotes.get(1), found.get(2L))).containsExactly(
                "SUPPLIED memory", "COMPANY SK Hynix Inc.", "COMPANY Micron Technology, Inc.", "COMPANY Samsung");
        // The answer gives the same phrase twice, so it is marked in both places.
        assertThat(marked(quotes.get(2), found.get(3L))).containsExactly(
                "COMPANY Taiwan Semiconductor Manufacturing Company Limited", "COMPANY TSMC", "RELIANCE all",
                "SUPPLIED wafers for microprocessor and GPU products", "RELIANCE primarily",
                "COMPANY GLOBALFOUNDRIES Inc.", "COMPANY GF", "SUPPLIED wafers for microprocessor and GPU products");
        assertThat(marked(quotes.get(3), found.get(4L))).containsExactly(
                "FIGURE October 2025", "COMPANY OpenAI OpCo, LLC", "COMPANY OpenAI", "FIGURE 6 gigawatts",
                "SUPPLIED AMD GPUs", "SUPPLIED AMD Instinct MI450 series products");
        assertThat(marked(quotes.get(4), found.get(5L))).containsExactly(
                "SUPPLIED Manufacturing Services", "COMPANY Sanmina", "FIGURE five years");
        verify(calls).finish(CALL_ID, 26, 0);
    }

    @Test
    void aPhraseThatIsNotInItsQuoteWordForWordIsNotKept() {
        stubAnswer(handWritten(1,
                "sole supplier", "COMPANY", // not in the quote
                "Hynix SK", "COMPANY", // its words out of order
                "SK Hynix Micron", "COMPANY", // words from different places
                "SK Hynix Limited", "COMPANY", // one word different
                "memor", "SUPPLIED", // only part of a word
                "SK Hynix Inc.", "COMPANY"));

        List<RelationshipHighlight> kept = finder.find(List.of(relationship(1L, MEMORY))).get(1L);

        assertThat(marked(MEMORY, kept)).containsExactly("COMPANY SK Hynix Inc.");
        verify(calls).finish(CALL_ID, 1, 5);
    }

    @Test
    void capitalsSpacingAndPunctuationAreIgnoredButTheReportsOwnWordsAreMarked() {
        stubAnswer(handWritten(1, "sk   hynix inc", "COMPANY", "MICRON TECHNOLOGY INC", "COMPANY"));

        List<RelationshipHighlight> kept = finder.find(List.of(relationship(1L, MEMORY))).get(1L);

        assertThat(marked(MEMORY, kept)).containsExactly("COMPANY SK Hynix Inc", "COMPANY Micron Technology, Inc");
    }

    @Test
    void punctuationAtAPhrasesEndsIsMarkedWhenTheEvidenceHasItToo() {
        stubAnswer(handWritten(1, "13%", "FIGURE"));

        List<RelationshipHighlight> kept = finder.find(List.of(relationship(1L, SHARE_OF_REVENUE))).get(1L);

        assertThat(marked(SHARE_OF_REVENUE, kept)).containsExactly("FIGURE 13%");
    }

    @Test
    void whereTwoPhrasesOverlapTheLongerIsKept() {
        stubAnswer(handWritten(1, "Hynix", "FIGURE", "SK Hynix Inc.", "COMPANY", "Hynix Inc., Micron", "RELIANCE"));

        List<RelationshipHighlight> kept = finder.find(List.of(relationship(1L, MEMORY))).get(1L);

        assertThat(marked(MEMORY, kept)).containsExactly("RELIANCE Hynix Inc., Micron");
    }

    @Test
    void ofTwoOverlappingPhrasesAsLongTheOneGivenFirstIsKept() {
        stubAnswer(handWritten(1, "memory", "SUPPLIED", "memory", "FIGURE"));

        List<RelationshipHighlight> kept = finder.find(List.of(relationship(1L, MEMORY))).get(1L);

        assertThat(marked(MEMORY, kept)).containsExactly("SUPPLIED memory");
    }

    @Test
    void aKindOtherThanTheFourIsNotKept() {
        stubAnswer(handWritten(1, "memory", "PRODUCT"));

        assertThat(finder.find(List.of(relationship(1L, MEMORY))).get(1L)).isEmpty();
    }

    @Test
    void atMostEightPhrasesAreTakenFromAQuote() {
        stubAnswer(handWritten(1, "We", "COMPANY", "purchase", "COMPANY", "memory", "COMPANY", "from", "COMPANY",
                "SK", "COMPANY", "Hynix", "COMPANY", "Inc", "COMPANY", "Micron", "COMPANY",
                "Technology", "COMPANY", "Samsung", "COMPANY"));

        assertThat(finder.find(List.of(relationship(1L, MEMORY))).get(1L)).hasSize(8);
        verify(calls).finish(CALL_ID, 8, 2);
    }

    @Test
    void aQuoteWithNothingFoundIsAnsweredWithNoHighlightsSoItIsNotReadAgain() {
        stubAnswer("[{\"index\":1,\"highlights\":[]}]");

        Map<Long, List<RelationshipHighlight>> found = finder.find(
                List.of(relationship(1L, MEMORY), relationship(2L, SHARE_OF_REVENUE)));

        assertThat(found).containsOnlyKeys(1L, 2L);
        assertThat(found.get(1L)).isEmpty();
        assertThat(found.get(2L)).isEmpty();
    }

    @Test
    void aPhraseForAQuoteThatWasNeverAskedIsNotKept() {
        stubAnswer(handWritten(2, "memory", "SUPPLIED"));

        assertThat(finder.find(List.of(relationship(1L, MEMORY))).get(1L)).isEmpty();
        verify(calls).finish(CALL_ID, 0, 1);
    }

    @Test
    void anUnreadableAnswerIsRecordedAsUnreadableAndNothingIsFoundForTheBatch() {
        when(gemini.generateHighlights(any())).thenReturn(new AiAnswer("This is not the JSON asked for.", CALL_ID));

        Map<Long, List<RelationshipHighlight>> found = finder.find(List.of(relationship(1L, MEMORY)));

        assertThat(found).isEmpty();
        verify(calls).unreadable(CALL_ID);
        verify(calls, never()).finish(anyLong(), anyInt(), anyInt());
    }

    @Test
    void theRecordedPromptNamesEachQuoteAndHoldsNoReportText() {
        stubAnswer("[]");

        finder.find(List.of(relationship(7L, MEMORY), relationship(8L, SHARE_OF_REVENUE)));

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generateHighlights(sent.capture());
        assertThat(sent.getValue().purpose()).isEqualTo(AiPurpose.HIGHLIGHT);
        assertThat(sent.getValue().reportId()).isNull();
        assertThat(sent.getValue().prompt()).contains("1. " + MEMORY + "\n", "2. " + SHARE_OF_REVENUE + "\n");
        assertThat(sent.getValue().recordedPrompt())
                .contains("1. [quote of relationship 7]\n", "2. [quote of relationship 8]\n")
                .doesNotContain(MEMORY, SHARE_OF_REVENUE);
    }

    /** Each highlight as its kind and the words it covers in the evidence, in order. */
    private static List<String> marked(String evidence, List<RelationshipHighlight> highlights) {
        return highlights.stream()
                .map(h -> h.getKind() + " " + evidence.substring(h.getStartAt(), h.getEndAt()))
                .toList();
    }

    private static Relationship relationship(Long id, String evidence) {
        Relationship relationship = new Relationship(1L, 1L, 2L, RelationshipType.SUPPLIER, null, evidence);
        ReflectionTestUtils.setField(relationship, "id", id);
        return relationship;
    }

    /** Gemini answers with this text, in the envelope its API uses. */
    private void stubAnswer(String text) {
        String response = json.writeValueAsString(Map.of("candidates", List.of(
                Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
        when(gemini.generateHighlights(any())).thenReturn(new AiAnswer(response, CALL_ID));
    }

    /**
     * An answer written by hand for this test, never by Gemini, because its real answer has no such mistakes: the quote's
     * number, then each phrase followed by its kind.
     */
    private String handWritten(int index, String... phrasesAndKinds) {
        List<Map<String, String>> highlights = new ArrayList<>();
        for (int i = 0; i < phrasesAndKinds.length; i += 2) {
            highlights.add(Map.of("text", phrasesAndKinds[i], "kind", phrasesAndKinds[i + 1]));
        }
        return json.writeValueAsString(List.of(Map.of("index", index, "highlights", highlights)));
    }
}
