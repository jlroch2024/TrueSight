package com.truesight.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One holding, as the website sees it. {@code weight} is empty when the CSV gave none.
 *
 * <p>The company and report fields are empty until the analysis has matched the ticker to a company and found its
 * annual report. {@code reportType} is the form, e.g. "10-K"; {@code reportLink} opens it on the SEC website.
 */
public record HoldingResponse(
        Long id,
        String ticker,
        BigDecimal weight,
        String companyName,
        HoldingStatus status,
        String statusReason,
        String reportType,
        LocalDate reportDate,
        String reportLink) {
}
