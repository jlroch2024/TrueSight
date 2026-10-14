package com.truesight.news;

/** One ordered step in the portfolio news-check workflow. */
public interface NewsCheckStep {
    void run(Long portfolioId);
}
