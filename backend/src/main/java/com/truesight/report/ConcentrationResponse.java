package com.truesight.report;

import com.truesight.relationship.RelationshipResponse.CompanySummary;
import com.truesight.relationship.RelationshipResponse.ReportSummary;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One dependency figure, such as "Apple: 91% of revenue for the year to 28 March 2026". {@code counterparty} is null
 * when the report does not name the company ("Customer A"). {@code share} is from 0 to 1.
 */
public record ConcentrationResponse(
        String label,
        CompanySummary counterparty,
        ConcentrationKind kind,
        ConcentrationMeasure measure,
        BigDecimal share,
        LocalDate periodStart,
        LocalDate periodEnd,
        ReportSummary report) {
}
