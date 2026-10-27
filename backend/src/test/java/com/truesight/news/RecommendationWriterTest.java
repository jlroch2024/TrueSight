package com.truesight.news;

import tools.jackson.databind.json.JsonMapper;
import com.truesight.support.Examples;
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

    @Test
    void readsTheRealRecommendationFromTheGeminiResponseEnvelope() {
        RecommendationWriter writer = new RecommendationWriter(null, null, null, null, null, null, null,
                JsonMapper.builder().build());

        assertThat(writer.read(Examples.geminiRecommendation())).isEqualTo(
                "Monitor updates from Nvidia and its regional distribution partners regarding freight security "
                        + "protocols in Latin America to verify whether additional transit disruptions or shipment "
                        + "delays affect RTX 5090 retail availability.");
    }
}
