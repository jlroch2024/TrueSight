package com.truesight.news;

import java.util.List;

/**
 * The alert list, and how to read each alert's exposure: a total weight when the portfolio is weighted, otherwise a
 * count of holdings out of holdingCount.
 */
public record AlertsResponse(boolean weighted, int holdingCount, List<AlertResponse> alerts) {
}
