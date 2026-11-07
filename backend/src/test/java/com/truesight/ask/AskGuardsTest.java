package com.truesight.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The checks that keep an answer inside its rules whatever Gemini says. */
class AskGuardsTest {

    @Test
    void anIdIsReadHoweverGeminiWritesIt() {
        assertThat(AskGuards.idKey("R12")).isEqualTo("R12");
        assertThat(AskGuards.idKey("[r12]")).isEqualTo("R12");
        assertThat(AskGuards.idKey(" h 1 ")).isEqualTo("H1");
        assertThat(AskGuards.idKey(null)).isEmpty();
    }

    @Test
    void theTwoFixedSentencesAreRefusalsEvenWithOtherWhitespaceOrACurlyApostrophe() {
        assertThat(AskGuards.refusalOf("TrueSight's data does not answer that.")).isEqualTo(AskGuards.UNKNOWN);
        assertThat(AskGuards.refusalOf("  TrueSight’s data does\n not answer that. ")).isEqualTo(AskGuards.UNKNOWN);
        assertThat(AskGuards.refusalOf(AskGuards.NO_ADVICE)).isEqualTo(AskGuards.NO_ADVICE);
        assertThat(AskGuards.refusalOf("TrueSight's data does not answer that, sorry.")).isNull();
        assertThat(AskGuards.isRefusal("Anything else.")).isFalse();
    }

    @Test
    void theNoAdviceSentenceDoesNotContainTheWordsTheAdviceCheckLooksFor() {
        assertThat(AskGuards.isAdvice(AskGuards.NO_ADVICE, "")).isFalse();
        assertThat(AskGuards.isAdvice(AskGuards.UNKNOWN, "")).isFalse();
    }

    @Test
    void anAnswerThatTellsTheUserToTradeIsAdvice() {
        assertThat(AskGuards.isAdvice("You should buy more AMD.", "")).isTrue();
        assertThat(AskGuards.isAdvice("Consider a SELL rating and a price target of 150.", "")).isTrue();
        assertThat(AskGuards.isAdvice("Hold your Nvidia shares.", "")).isTrue();
    }

    @Test
    void wordsThatMerelyContainTheForbiddenOnesAreNotAdvice() {
        assertThat(AskGuards.isAdvice("Its holdings include a shareholder buyback plan.", "")).isFalse();
    }

    @Test
    void anAnswerThatSaysOneCompanyBuysSellsOrHoldsSomethingFromAnotherIsNotAdvice() {
        assertThat(AskGuards.isAdvice("Dell and HP buy GPUs from NVIDIA.", "")).isFalse();
        assertThat(AskGuards.isAdvice("AMD and NVIDIA sell to the same cloud customers.", "")).isFalse();
        assertThat(AskGuards.isAdvice("Apple and Samsung hold supply agreements with TSMC.", "")).isFalse();
        assertThat(AskGuards.isAdvice("NVIDIA sells to Dell [R1], which buys and holds inventory.", "")).isFalse();
    }

    @Test
    void anAnswerThatAdvisesTheUserInOtherWordsIsStillAdvice() {
        assertThat(AskGuards.isAdvice("You may want to sell NVIDIA.", "")).isTrue();
        assertThat(AskGuards.isAdvice("Investors should consider buying TSMC.", "")).isTrue();
        assertThat(AskGuards.isAdvice("I recommend holding Apple.", "")).isTrue();
        assertThat(AskGuards.isAdvice("NVIDIA is rated overweight.", "")).isTrue();
        assertThat(AskGuards.isAdvice("Dell supplies HP. Sell NVIDIA now.", "")).isTrue();
    }

    @Test
    void wordsInsideAQuotationCopiedFromTheRecordsAreTheReportsNotTheAnswers() {
        String records = "[R1] A supplies B. Evidence: 'We sell our wafers to a limited number of customers.'";

        assertThat(AskGuards.isAdvice("The report says \"We sell our wafers to a limited number of customers\".", records))
                .isFalse();
        // The same words outside a copied quotation are the answer's own.
        assertThat(AskGuards.isAdvice("You should sell your wafers.", records)).isTrue();
        // A quotation that is not in the records is not exempt.
        assertThat(AskGuards.isAdvice("An analyst said \"buy it now\".", records)).isTrue();
    }

    @Test
    void anAnswerMentionsUnreviewedInAnyCapitals() {
        assertThat(AskGuards.mentionsUnreviewed("All of these links are Unreviewed.")).isTrue();
        assertThat(AskGuards.mentionsUnreviewed("No analyst has looked at them.")).isFalse();
    }

    @Test
    void anAnswerThatSaysThereAreNoUnreviewedRelationshipsDoesNotMentionThem() {
        assertThat(AskGuards.mentionsUnreviewed("There are no unreviewed relationships here.")).isFalse();
        assertThat(AskGuards.mentionsUnreviewed("None of them is unreviewed.")).isFalse();
        assertThat(AskGuards.mentionsUnreviewed("This link isn't unreviewed.")).isFalse();
        // A negation in another sentence does not cancel it.
        assertThat(AskGuards.mentionsUnreviewed("There is no doubt about TSMC. The other link is unreviewed.")).isTrue();
    }
}
