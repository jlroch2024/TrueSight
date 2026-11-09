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
import java.util.UUID;
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
        when(gemini.generateSecondCheck(any(AiRequest.class))).thenAnswer(invocation ->
                answer(Examples.geminiNvidiaSecondCheckFor(invocation.<AiRequest>getArgument(0).prompt())));
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
                .andExpect(jsonPath("$[0].confidence.reasons", hasSize(4)))
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
    // ---- Both Reports Name It ------------------------------------------------------------------------------------

    @Test
    void qualcommsAndAmkorsOwnReportsAgreeSoBothRiseAndEachCarriesTheOthersSentence() throws Exception {
        String qualcommSays = "Our primary semiconductor assembly and test suppliers are Advanced Semiconductor Engineering, "
                + "Amkor Technology, Siliconware Precision Industries and STATSChipPAC.";
        String amkorSays = "Direct sales to Apple and Qualcomm accounted for 29.8% and 11.1% of our net sales, respectively, "
                + "for the year ended December 31, 2025.";
        assertThat(Examples.qualcomm10K()).contains(qualcommSays);
        assertThat(Examples.amkor10K()).contains(amkorSays);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Company qualcomm = companyDirectory.findOrCreate("Qualcomm Test " + suffix, null, null, List.of());
        Company amkor = companyDirectory.findOrCreate("Amkor Test " + suffix, null, null, List.of());
        Report qualcommReport = reports.save(new Report(qualcomm.getId(), "10-K", "q-" + suffix, LocalDate.parse("2025-11-05"),
                "https://www.sec.gov/qualcomm", Examples.qualcomm10K()));
        Report amkorReport = reports.save(new Report(amkor.getId(), "10-K", "a-" + suffix, LocalDate.parse("2026-02-20"),
                "https://www.sec.gov/amkor", Examples.amkor10K()));
        Relationship inQualcomms = new Relationship(qualcommReport.getId(), qualcomm.getId(), amkor.getId(),
                RelationshipType.SUPPLIER, "assembly and test", qualcommSays);
        inQualcomms.setQuoteMatch(new BigDecimal("1.000"));
        relationships.save(inQualcomms);
        Relationship inAmkors = new Relationship(amkorReport.getId(), amkor.getId(), qualcomm.getId(),
                RelationshipType.CUSTOMER, "assembly and test", amkorSays);
        relationships.save(inAmkors);
        String email = "confidence-both-" + suffix + "@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Both Reports"));
        Holding holding = new Holding(portfolio.getId(), "QTST", new BigDecimal("10"));
        holding.setCompanyId(qualcomm.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].confidence.level").value("HIGH"))
                .andExpect(jsonPath("$[0].confidence.reasons[3].text").value("Both Reports Name It"))
                .andExpect(jsonPath("$[0].confidence.reasons[3].effect").value("RAISES"))
                .andExpect(jsonPath("$[0].agreesWith.id").value(inAmkors.getId()))
                .andExpect(jsonPath("$[0].agreesWith.evidence").value(amkorSays))
                .andExpect(jsonPath("$[0].agreesWith.report.url").value("https://www.sec.gov/amkor"));
    }

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
