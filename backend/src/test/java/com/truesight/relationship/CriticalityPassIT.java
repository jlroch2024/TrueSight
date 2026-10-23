package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The analyst-only pass annotates existing supplier relationships from the stored report. */
@IntegrationTest
class CriticalityPassIT {
    // Exact sentence from ASML's 2025 20-F; see ASML_REPORT_URL.

    private static final String SOLE_SOURCE_SENTENCE = "The number of lithography systems we are able to produce is "
            + "limited by the production capacity of one of our key suppliers, Carl Zeiss SMT, our sole supplier of "
            + "lenses, mirrors, illuminators, collectors and other critical optical components (which we refer to as optics).";
    private static final String ASML_REPORT_URL =
            "https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired AiCallLog aiCalls;
    @Autowired CriticalityPassService pass;
    @MockitoBean GeminiClient gemini;
    @MockitoBean CriticalityJob job;

    @Test
    void thePassFillsCriticalityOnExistingRelationshipsAndAddsNone() throws Exception {
        Seed seed = seed("criticality-pass-fields");
        String response = response(List.of(
                finding("Carl Zeiss SMT", "SOLE_SOURCE", SOLE_SOURCE_SENTENCE),
                finding("Apple Inc.", "SOLE_SOURCE", SOLE_SOURCE_SENTENCE)));
        stubAnswer(seed.report().getId(), response);

        pass.assess(seed.report().getId());
        Relationship updated = relationships.findById(seed.supplier().getId()).orElseThrow();

        assertThat(relationships.findByReportIdOrderByIdAsc(seed.report().getId())).hasSize(2);
        assertThat(updated.getCriticality()).isEqualTo(Criticality.SOLE_SOURCE);
        assertThat(updated.getCriticalityEvidence()).isEqualTo(SOLE_SOURCE_SENTENCE);
        assertThat(updated.getProvides()).isEqualTo(seed.supplier().getProvides());
        assertThat(updated.getEvidence()).isEqualTo(seed.supplier().getEvidence());
        assertThat(updated.getReviewStatus()).isEqualTo(seed.supplier().getReviewStatus());
        assertThat(updated.getType()).isEqualTo(RelationshipType.SUPPLIER);
        Relationship customer = relationships.findById(seed.customer().getId()).orElseThrow();
        assertThat(customer.getCriticality()).isNull();
        assertThat(customer.getCriticalityEvidence()).isNull();
        verify(gemini, times(1)).generateCriticality(any(AiRequest.class));
    }

    @Test
    void runningThePassTwiceChangesNothingMore() throws Exception {
        Seed seed = seed("criticality-pass-idempotent");
        stubAnswer(seed.report().getId(), response(List.of(
                finding("Carl Zeiss SMT", "SOLE_SOURCE", SOLE_SOURCE_SENTENCE))));
        pass.assess(seed.report().getId());
        Relationship first = relationships.findById(seed.supplier().getId()).orElseThrow();
        pass.assess(seed.report().getId());
        Relationship second = relationships.findById(seed.supplier().getId()).orElseThrow();
        assertThat(second.getCriticality()).isEqualTo(first.getCriticality());
        assertThat(second.getCriticalityEvidence()).isEqualTo(first.getCriticalityEvidence());
        verify(gemini, times(1)).generateCriticality(any(AiRequest.class));
    }

    @Test
    void analystsCanStartThePassAndReceiveAccepted() throws Exception {
        mockMvc.perform(post("/api/reports/criticality")
                        .header("Authorization", logins.analystBearer("analyst-criticality-accepted@example.com")))
                .andExpect(status().isAccepted());
    }

    @Test
    void portfolioManagersGetForbiddenFromThePass() throws Exception {
        mockMvc.perform(post("/api/reports/criticality")
                        .header("Authorization", logins.bearer("pm-criticality-forbidden@example.com")))
                .andExpect(status().isForbidden());
        verify(gemini, never()).generateCriticality(any(AiRequest.class));
    }

    @Test
    void aCriticalityAnswerForAnUnknownCounterpartyIsIgnored() throws Exception {
        Seed seed = seed("criticality-pass-unknown");
        stubAnswer(seed.report().getId(), response(List.of(
                finding("Counterparty Not In Existing Relationships", "SOLE_SOURCE", SOLE_SOURCE_SENTENCE))));

        pass.assess(seed.report().getId());

        Relationship unchanged = relationships.findById(seed.supplier().getId()).orElseThrow();
        assertThat(unchanged.getCriticality()).isNull();
        assertThat(unchanged.getCriticalityEvidence()).isNull();
        assertThat(relationships.findByReportIdOrderByIdAsc(seed.report().getId())).hasSize(2);
    }

    @Test
    void reportsAlreadyAskedWithTheCriticalityEnabledRelationshipPromptAreSkipped() throws Exception {
        Seed seed = seed("criticality-pass-skip-new-prompt");
        AiRequest request = new AiRequest(AiPurpose.FIND_RELATIONSHIPS,
                "relationship prompt with criticalityQuote", seed.report().getId(),
                "relationship prompt with criticalityQuote");
        Long callId = aiCalls.record(request, "test-model", "[]", AiOutcome.ANSWERED, null, Instant.now(), 1);
        aiCalls.attachReport(callId, seed.report().getId());

        assertThat(pass.pendingReportIds()).doesNotContain(seed.report().getId());

        startAsAnalyst();

        assertThat(relationships.findById(seed.supplier().getId()).orElseThrow().getCriticality()).isNull();
        verify(gemini, never()).generateCriticality(any(AiRequest.class));
    }

    private void startAsAnalyst() throws Exception {
        mockMvc.perform(post("/api/reports/criticality")
                        .header("Authorization", logins.analystBearer("analyst-criticality-pass@example.com")))
                .andExpect(status().isAccepted());
    }

    private Seed seed(String suffix) {
        Company asml = companyDirectory.findOrCreate("ASML Holding N.V.", "0000937966", "ASML", List.of());
        Company carlZeiss = companyDirectory.findOrCreate("Carl Zeiss SMT", null, null, List.of());
        Company apple = companyDirectory.findOrCreate("Apple Inc.", "0000320193", "AAPL", List.of());
        Report report = reports.save(new Report(asml.getId(), "20-F", "criticality-" + suffix,
                LocalDate.parse("2026-02-25"), ASML_REPORT_URL, SOLE_SOURCE_SENTENCE));
        Relationship supplier = relationships.save(new Relationship(report.getId(), asml.getId(), carlZeiss.getId(),
                RelationshipType.SUPPLIER, "optics", SOLE_SOURCE_SENTENCE));
        supplier.setReviewStatus(ReviewStatus.APPROVED);
        supplier.setReviewNote("Reviewed before the pass");
        supplier = relationships.save(supplier);
        Relationship customer = relationships.save(new Relationship(report.getId(), asml.getId(), apple.getId(),
                RelationshipType.CUSTOMER, "lithography systems", SOLE_SOURCE_SENTENCE));
        return new Seed(report, supplier, customer);
    }

    private void stubAnswer(Long reportId, String response) {
        when(gemini.generateCriticality(any(AiRequest.class))).thenAnswer(invocation -> {
            AiRequest request = invocation.getArgument(0);
            Long callId = aiCalls.record(request, "test-model", response, AiOutcome.ANSWERED, null, Instant.now(), 1);
            aiCalls.attachReport(callId, request.reportId());
            return new AiAnswer(response, callId);
        });
    }

    private static Map<String, Object> finding(String company, String criticality, String quote) {
        return Map.of("company", company, "criticality", criticality, "criticalityQuote", quote);
    }

    private static String response(List<Map<String, Object>> findings) throws Exception {
        String content = JsonMapper.builder().build().writeValueAsString(findings);
        return JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", content)))))));
    }

    private record Seed(Report report, Relationship supplier, Relationship customer) { }
}
