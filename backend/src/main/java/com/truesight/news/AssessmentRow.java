package com.truesight.news;

/** One judged article and its judgement, as {@link NewsAssessmentRepository#findJudgedSince} reads them. */
public record AssessmentRow(NewsAssessment assessment, NewsArticle article) {
}
