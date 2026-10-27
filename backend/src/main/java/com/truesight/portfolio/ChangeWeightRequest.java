package com.truesight.portfolio;

import java.math.BigDecimal;

/** A holding's new weight: a percentage from 0 to 100, or empty to say it has none. Checked in {@link HoldingService}. */
public record ChangeWeightRequest(BigDecimal weight) {
}
