package com.truesight.portfolio;

import java.math.BigDecimal;

/**
 * One holding to add to a portfolio. {@code weight} is a percentage from 0 to 100, or empty for none. Both fields
 * are checked in {@link HoldingService}, so a refusal is a plain sentence for the person using the page.
 */
public record AddHoldingRequest(String ticker, BigDecimal weight) {
}
