package com.truesight.news;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Runs every registered news-check step in its declared {@code @Order}. */
@Component
public class NewsCheckStepRunner {

    private final List<NewsCheckStep> steps;

    public NewsCheckStepRunner(List<NewsCheckStep> steps) {
        ArrayList<NewsCheckStep> orderedSteps = new ArrayList<>(steps);
        AnnotationAwareOrderComparator.sort(orderedSteps);
        this.steps = List.copyOf(orderedSteps);
    }

    /** Call after Get News has saved newly found articles for this portfolio. */
    public void run(Long portfolioId) {
        steps.forEach(step -> step.run(portfolioId));
    }
}
