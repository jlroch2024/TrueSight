package com.truesight.relationship;

import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * See How Confident TrueSight Is, end to end: analysing NVIDIA's real 10-K records each quote's match, and the
 * relationships carry their confidence.
 *
 * <p>Gemini is replaced by a fake giving its two real answers from {@code docs/examples/}: the relationships it found,
 * whose seven quotes are all word for word in the report, and the second check, which agrees with all seven. Nothing
 * here calls the SEC or Gemini.
 */
@IntegrationTest
class RelationshipConfidenceIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String NVIDIA_ACCESSION = "0001045810-26-000021";
    private static final String NVIDIA_URL =
            "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void saveNvidiasReportAndFakeGemini() {
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of());
        reports.deleteByCompanyId(nvidia.getId()); // and, with it, any relationships found in it before
        reports.save(new Report(nvidia.getId(), "10-K", NVIDIA_ACCESSION, LocalDate.parse("2026-02-25"),
                NVIDIA_URL, Examples.nvidia10K()));
        when(sec.companyForTicker("NVDA")).thenReturn(new SecClient.SecCompany("NVIDIA Corporation", NVIDIA_CIK, "NVDA"));
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidiaSecondCheck()));
    }

    @Test
    void analysingAReportRecordsEachQuotesMatch() throws Exception {
        analyse("confidence-match@example.com");

        // Every quote in Gemini's real answer is word for word in the report.
        assertThat(nvidiaRelationships()).hasSize(7).allSatisfy(relationship ->
                assertThat(relationship.getQuoteMatch()).isEqualByComparingTo(new BigDecimal("1.000")));
    }

    @Test
    void aStrongMatchTheSecondCheckAgreesWithIsHighWithItsReasons() throws Exception {
        String email = "confidence-high@example.com";
        Portfolio portfolio = analyse(email);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[*].confidence.level", everyItem(is("HIGH"))))
                .andExpect(jsonPath("$[0].confidence.reasons", hasSize(3)))
                .andExpect(jsonPath("$[0].confidence.reasons[0].text").value("Not Yet Reviewed"))
                .andExpect(jsonPath("$[0].confidence.reasons[0].effect").value("NONE"))
                .andExpect(jsonPath("$[0].confidence.reasons[1].text").value("Strong Quote Match (100%)"))
                .andExpect(jsonPath("$[0].confidence.reasons[1].effect").value("RAISES"))
                .andExpect(jsonPath("$[0].confidence.reasons[2].text").value("Second Check Agrees"))
                .andExpect(jsonPath("$[0].confidence.reasons[2].effect").value("RAISES"));
    }

    @Test
    void aRelationshipFoundBeforeTheMatchWasRecordedSaysSoAndIsJudgedOnTheOtherTwo() throws Exception {
        String email = "confidence-before@example.com";
        Portfolio portfolio = analyse(email);
        Relationship older = nvidiaRelationships().getFirst();
        older.setQuoteMatch(null); // as every relationship found before this story is
        relationships.save(older);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].confidence.level", older.getId()).value("MEDIUM"))
                .andExpect(jsonPath("$[?(@.id == %d)].confidence.reasons[1].text", older.getId())
                        .value("Quote Match Not Recorded"));
    }

    @Test
    void anApprovedRelationshipIsHighBecauseAnAnalystApprovedIt() throws Exception {
        String email = "confidence-approved@example.com";
        Portfolio portfolio = analyse(email);
        Relationship relationship = nvidiaRelationships().getFirst();
        mockMvc.perform(put("/api/relationships/{id}/review", relationship.getId())
                        .header("Authorization", logins.analystBearer("confidence-analyst@example.com"))
                        .contentType("application/json").content("{\"decision\": \"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship.confidence.level").value("HIGH"))
                .andExpect(jsonPath("$.relationship.confidence.reasons[0].text").value("An Analyst Approved It"))
                .andExpect(jsonPath("$.relationship.confidence.reasons[0].effect").value("RAISES"));

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[?(@.id == %d)].confidence.reasons[0].text", relationship.getId())
                        .value("An Analyst Approved It"));
    }

    /** Makes a portfolio holding NVIDIA, presses Analyse, and waits for the holding to be Done. */
    private Portfolio analyse(String email) throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Confidence " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "NVDA", new BigDecimal("100")));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 400; attempt++) {
            if (holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus() == HoldingStatus.DONE
                    && !nvidiaRelationships().isEmpty()
                    && nvidiaRelationships().stream().allMatch(r -> r.getSecondCheck() != null)) {
                return portfolio;
            }
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(HoldingStatus.DONE);
        return portfolio;
    }

    /** NVIDIA's relationships, in the order Gemini listed them. */
    private List<Relationship> nvidiaRelationships() {
        return relationships.findByReportIdOrderByIdAsc(
                reports.findByAccessionNumber(NVIDIA_ACCESSION).orElseThrow().getId());
    }
}
