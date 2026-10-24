package com.truesight.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks that the news prompt states the sharper supply-chain test without changing its answer shape. */
class NewsAssessorPromptTest {

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
}
