package com.truesight.news;

/** One joined database row used to build an alert response without mapped entity relationships. */
public record AlertRow(Alert alert, NewsArticle article, NewsAssessment assessment) {
}
