package com.truesight.ask;

import com.truesight.news.AlertsResponse;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.support.Examples;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The sample portfolio of {@code docs/examples/} as the records Ask TrueSight sends to Gemini: its ten holdings
 * ({@code holdings.csv}) and the 17 real relationships found in their annual reports ({@code relationships.json}), with
 * no alerts. The saved Gemini answers in the same folder were given to exactly these records, so their ids ([H1], [R3]
 * ...) mean the same thing here.
 *
 * <p>The report each holding was analysed from is the README's table of companies, reports and filing dates.
 */
final class AskSample {

    /** Ticker to report form and filing date, from the table in docs/examples/README.md. */
    private static final Map<String, String> REPORTS = Map.of(
            "NVDA", "10-K 2026-02-25", "AMD", "10-K 2026-02-04", "QCOM", "10-K 2025-11-05",
            "TSLA", "10-K 2026-01-29", "AVGO", "10-K 2025-12-18", "LRCX", "10-K 2026-08-07",
            "CRUS", "10-K 2026-05-21", "ASML", "20-F 2026-02-25", "STM", "20-F 2026-02-26",
            "SONY", "20-F 2026-06-18");

    private AskSample() {
    }

    /** The 17 real relationships, in the order of the file (and so of their [R1] to [R17] ids). */
    static List<RelationshipResponse> relationships() {
        return JsonMapper.builder().build().readValue(Examples.relationships(),
                new TypeReference<List<RelationshipResponse>>() {
                });
    }

    /** The ten holdings, each analysed, with the company names the relationships give them. */
    static List<HoldingResponse> holdings() {
        List<RelationshipResponse> links = relationships();
        List<HoldingResponse> holdings = new ArrayList<>();
        String[] lines = Examples.holdingsCsv().strip().split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String[] cells = lines[i].split(",");
            String ticker = cells[0].strip();
            RelationshipResponse.CompanySummary company = links.stream()
                    .map(RelationshipResponse::company)
                    .filter(found -> ticker.equals(found.ticker()))
                    .findFirst().orElse(null);
            String[] report = REPORTS.get(ticker).split(" ");
            holdings.add(new HoldingResponse((long) i, ticker, new BigDecimal(cells[1].strip()),
                    company == null ? null : company.name(), company == null ? null : company.displayName(),
                    HoldingStatus.DONE, null, report[0], LocalDate.parse(report[1]), "https://www.sec.gov/", null));
        }
        // The order HoldingService.list gives them in, which is the order of the records ([H1] is AMD).
        holdings.sort(java.util.Comparator.comparing(HoldingResponse::ticker));
        return holdings;
    }

    static AlertsResponse noAlerts() {
        return new AlertsResponse(true, 10, List.of());
    }

    /** The records for the whole sample portfolio. */
    static AskRecords.Built records() {
        return AskRecords.build(holdings(), relationships(), noAlerts());
    }
}
