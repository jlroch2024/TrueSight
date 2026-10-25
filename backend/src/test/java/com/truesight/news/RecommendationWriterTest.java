package com.truesight.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationWriterTest {

    @Test
    void withholdsWholeForbiddenWordsWithoutMatchingPartsOfOtherWords() {
        for (String term : new String[]{"buy", "sell", "hold", "price target", "overweight", "underweight"}) {
            assertThat(RecommendationWriter.isInvestmentAdvice("Please " + term.toUpperCase() + " this."))
                    .as(term).isTrue();
        }
        assertThat(RecommendationWriter.isInvestmentAdvice("Build a supplier delivery watchlist." )).isFalse();
        assertThat(RecommendationWriter.isInvestmentAdvice("Shareholder buyback plans changed.")).isFalse();
        assertThat(RecommendationWriter.isInvestmentAdvice("The company is on holdover until review.")).isFalse();
    }
}
