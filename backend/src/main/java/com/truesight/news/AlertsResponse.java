package com.truesight.news;

import java.util.List;

/** The alert list and the article totals from this portfolio's latest seven-day news view. */
public record AlertsResponse(int articlesChecked, int noiseCount, List<AlertResponse> alerts) {
}
