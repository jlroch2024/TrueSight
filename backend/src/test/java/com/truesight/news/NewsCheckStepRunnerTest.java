package com.truesight.news;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsCheckStepRunnerTest {

    @Test
    void runsEveryStepInOrderWithThePortfolioId() {
        List<String> calls = new ArrayList<>();
        NewsCheckStep later = new LaterStep(calls);
        NewsCheckStep earlier = new EarlierStep(calls);

        new NewsCheckStepRunner(List.of(later, earlier)).run(42L);

        assertThat(calls).containsExactly("first:42", "second:42");
    }

    @Order(1)
    private record EarlierStep(List<String> calls) implements NewsCheckStep {
        @Override
        public void run(Long portfolioId) {
            calls.add("first:" + portfolioId);
        }
    }

    @Order(2)
    private record LaterStep(List<String> calls) implements NewsCheckStep {
        @Override
        public void run(Long portfolioId) {
            calls.add("second:" + portfolioId);
        }
    }
}
