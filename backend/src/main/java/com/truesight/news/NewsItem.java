package com.truesight.news;

import java.time.Instant;

/** One headline returned by Google News before it is saved. */
public record NewsItem(String title, String url, String source, Instant publishedAt) {
}
