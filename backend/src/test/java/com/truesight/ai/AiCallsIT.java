package com.truesight.ai;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The analyst-facing list, detail and export of the calls recorded by {@link AiCallLog}. */
@IntegrationTest
class AiCallsIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired AiCallLog log;
    @Autowired CompanyDirectory companies;
    @Autowired ReportRepository reports;

    @Test
    void callsAreNewestFirstWithFiftyOnAPageAndEverySummaryColumn() throws Exception {
        Instant start = Instant.parse("2031-01-10T09:00:00Z");
        for (int index = 0; index < 51; index++) {
            record(AiPurpose.JUDGE_NEWS, AiOutcome.ANSWERED, start.plusSeconds(index), "model-" + index,
                    index, 50 - index);
        }
        String analyst = analyst("paging");

        mockMvc.perform(get("/api/ai-calls?from=2031-01-10&to=2031-01-10&page=0")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls", hasSize(50)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.total").value(51))
                .andExpect(jsonPath("$.calls[0].startedAt").value("2031-01-10T09:00:50Z"))
                .andExpect(jsonPath("$.calls[0].purpose").value("JUDGE_NEWS"))
                .andExpect(jsonPath("$.calls[0].model").value("model-50"))
                .andExpect(jsonPath("$.calls[0].outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.calls[0].durationMs").value(150))
                .andExpect(jsonPath("$.calls[0].kept").value(50))
                .andExpect(jsonPath("$.calls[0].dropped").value(0));

        mockMvc.perform(get("/api/ai-calls?from=2031-01-10&to=2031-01-10&page=1")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls", hasSize(1)))
                .andExpect(jsonPath("$.calls[0].startedAt").value("2031-01-10T09:00:00Z"));
    }

    @Test
    void purposeOutcomeAndDateFiltersEachNarrowTheList() throws Exception {
        record(AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED,
                Instant.parse("2032-02-10T12:00:00Z"), "model", 2, 1);
        record(AiPurpose.JUDGE_NEWS, AiOutcome.FAILED,
                Instant.parse("2032-02-11T12:00:00Z"), "model", null, null);
        String analyst = analyst("filters");

        mockMvc.perform(get("/api/ai-calls?purpose=FIND_RELATIONSHIPS&from=2032-02-01&to=2032-02-28")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls", hasSize(1)))
                .andExpect(jsonPath("$.calls[0].purpose").value("FIND_RELATIONSHIPS"));
        mockMvc.perform(get("/api/ai-calls?outcome=FAILED&from=2032-02-01&to=2032-02-28")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls", hasSize(1)))
                .andExpect(jsonPath("$.calls[0].outcome").value("FAILED"));
        mockMvc.perform(get("/api/ai-calls?from=2032-02-11&to=2032-02-11")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calls", hasSize(1)))
                .andExpect(jsonPath("$.calls[0].startedAt").value("2032-02-11T12:00:00Z"));
    }

    @Test
    void csvContainsExactlyTheFilteredSummariesWithoutPromptsOrAnswers() throws Exception {
        record(AiPurpose.COMMON_NAMES, AiOutcome.ANSWERED,
                Instant.parse("2033-03-10T12:00:00Z"), "gemini,\"audit\"", 4, 2);
        record(AiPurpose.RECOMMEND, AiOutcome.ANSWERED,
                Instant.parse("2033-03-10T13:00:00Z"), "excluded-model", 1, 0);

        MvcResult result = mockMvc.perform(get(
                                "/api/ai-calls/export?purpose=COMMON_NAMES&outcome=ANSWERED&from=2033-03-10&to=2033-03-10")
                        .header("Authorization", analyst("csv")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ai-calls.csv\""))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv)
                .contains("Started At,Purpose,Model,Outcome,Duration (ms),Kept,Dropped")
                .contains("\"2033-03-10T12:00:00Z\",\"COMMON_NAMES\",\"gemini,\"\"audit\"\"\",\"ANSWERED\",\"150\",\"4\",\"2\"")
                .doesNotContain("excluded-model")
                .doesNotContain("recorded prompt")
                .doesNotContain("exact answer");
    }

    @Test
    void openingACallShowsTheRecordedMarkerAndExactAnswerButNeverTheReportText() throws Exception {
        // NVIDIA's own words in the 10-K saved under docs/examples.
        String reportText = "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited.";
        Company company = companies.findOrCreate(
                "NVIDIA AI Calls Test " + UUID.randomUUID(), null, null, List.of());
        Report report = reports.save(new Report(company.getId(), "10-K",
                "AI" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                java.time.LocalDate.parse("2026-02-25"), "https://www.sec.gov/Archives/test-report", reportText));
        String prompt = "TEXT: " + AiRequest.reportMarker(report.getId(), reportText);
        Long id = log.record(new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "not recorded", report.getId(), prompt),
                "gemini-test-model", "{\"relationships\":[]}", AiOutcome.ANSWERED, null,
                Instant.parse("2034-04-10T12:00:00Z"), 123);
        log.attachReport(id, report.getId());

        mockMvc.perform(get("/api/ai-calls/{id}", id).header("Authorization", analyst("detail")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value(prompt))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Taiwan Semiconductor"))))
                .andExpect(jsonPath("$.response").value("{\"relationships\":[]}"))
                .andExpect(jsonPath("$.reportId").value(report.getId()))
                .andExpect(jsonPath("$.reportUrl").value("https://www.sec.gov/Archives/test-report"));
    }

    @Test
    void badFiltersAndAnUnknownCallGiveReadableClientErrors() throws Exception {
        String analyst = analyst("bad-input");
        mockMvc.perform(get("/api/ai-calls?purpose=OTHER").header("Authorization", analyst))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ai-calls?outcome=OTHER").header("Authorization", analyst))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ai-calls?from=10-04-2035").header("Authorization", analyst))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ai-calls?from=2035-04-11&to=2035-04-10").header("Authorization", analyst))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ai-calls/{id}", Long.MAX_VALUE).header("Authorization", analyst))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That AI call was not found."));
    }

    @Test
    void portfolioManagersGetA403FromTheListDetailAndExport() throws Exception {
        Long id = record(AiPurpose.CRITICALITY, AiOutcome.ANSWERED,
                Instant.parse("2036-06-10T12:00:00Z"), "model", 1, 0);
        String manager = logins.bearer("ai-calls-manager-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/ai-calls").header("Authorization", manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai-calls/{id}", id).header("Authorization", manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai-calls/export").header("Authorization", manager))
                .andExpect(status().isForbidden());
    }

    private Long record(AiPurpose purpose, AiOutcome outcome, Instant startedAt, String model,
                        Integer kept, Integer dropped) {
        Long id = log.record(AiRequest.of(purpose, "recorded prompt"), model, "exact answer", outcome,
                outcome == AiOutcome.FAILED ? "The AI could not be reached." : null, startedAt, 150);
        if (kept != null && dropped != null) log.finish(id, kept, dropped);
        return id;
    }

    private String analyst(String test) {
        return logins.analystBearer("ai-calls-" + test + "-" + UUID.randomUUID() + "@example.com");
    }
}
