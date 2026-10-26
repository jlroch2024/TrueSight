package com.truesight.news;

import jakarta.validation.constraints.Size;

public record RecommendationDecisionRequest(RecommendationDecision decision,
                                            @Size(max = 1000) String text,
                                            @Size(max = 500) String note) {
}
