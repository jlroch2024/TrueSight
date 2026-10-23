package com.truesight.news;

import java.util.List;

/** The alert list, and whether exposure should be read as portfolio weights or as holdings reached. */
// weighted describes the portfolio's current weights; each alert's exposure was stored when it was created.
public record AlertsResponse(boolean weighted, int holdingCount, List<AlertResponse> alerts) {
}
