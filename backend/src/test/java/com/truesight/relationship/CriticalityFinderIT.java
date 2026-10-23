package com.truesight.relationship;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.*;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.FakeAi;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CriticalityFinderIT {
    // Both quotes are verbatim from the official SEC filings linked immediately below.
    private static final String SOLE = "The number of lithography systems we are able to produce is limited by the production capacity of one of our key suppliers, Carl Zeiss SMT, our sole supplier of lenses, mirrors, illuminators, collectors and other critical optical components (which we refer to as optics).";
    private static final String SINGLE = "The ClearPoint Prism Neuro Laser Therapy System is supplied to us by CLS, and we rely on CLS as a single-source supplier for this product.";
    private static final String ASML_URL = "https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm";
    private static final String CLPT_URL = "https://www.sec.gov/Archives/edgar/data/1285550/000119312526174046/clpt_2025_annual_report.pdf";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void clearSeedReports() {
        companies.findByCik("0000937966").ifPresent(c -> reports.deleteByCompanyId(c.getId()));
        companies.findByCik("1285550").ifPresent(c -> reports.deleteByCompanyId(c.getId()));
    }

    @Test
    void aSoleSourceSentenceFromTheReportIsSavedAsTheRelationshipsCriticality() throws Exception {
        Portfolio p = seed("ASML", "ASML Holding N.V.", "0000937966", ASML_URL, SOLE);
        when(gemini.generate(any())).thenReturn(FakeAi.answer(answer(List.of(item("Carl Zeiss SMT", SOLE,
                "SOLE_SOURCE", SOLE)))));
        analyse(p, "ASML");
        relationships(p).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].criticality").value("SOLE_SOURCE"))
                .andExpect(jsonPath("$[0].criticalityEvidence").value(SOLE));
    }

    @Test
    void aSingleSourceSentenceIsSavedAsSingleSource() throws Exception {
        Portfolio p = seed("CLPT", "ClearPoint Neuro, Inc.", "1285550", CLPT_URL, SINGLE);
        when(gemini.generate(any())).thenReturn(FakeAi.answer(answer(List.of(item("CLS", SINGLE,
                "SINGLE_SOURCE", SINGLE)))));
        analyse(p, "CLPT");
        relationships(p).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].criticality").value("SINGLE_SOURCE"))
                .andExpect(jsonPath("$[0].criticalityEvidence").value(SINGLE));
    }

    @Test
    void aCriticalitySentenceNotInTheReportIsDroppedAndTheRelationshipIsStillSaved() throws Exception {
        Portfolio p = seed("ASML", "ASML Holding N.V.", "0000937966", ASML_URL, SOLE);
        when(gemini.generate(any())).thenReturn(FakeAi.answer(answer(List.of(item("Carl Zeiss SMT", SOLE,
                "SOLE_SOURCE", "Carl Zeiss SMT is the only possible supplier.")))));
        analyse(p, "ASML");
        relationships(p).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].criticality").value(nullValue()))
                .andExpect(jsonPath("$[0].criticalityEvidence").value(nullValue()));
    }

    private Portfolio seed(String ticker, String name, String cik, String url, String text) {
        Company company = companyDirectory.findOrCreate(name, cik, ticker, List.of());
        reports.save(new Report(company.getId(), ticker.equals("ASML") ? "20-F" : "10-K",
                "criticality-finder-" + ticker + "-" + System.nanoTime(), LocalDate.parse("2026-02-25"), url, text));
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany(name, cik, ticker));
        String email = "finder-" + ticker.toLowerCase() + "-" + System.nanoTime() + "@example.com";
        currentOwner = email;
        Portfolio p = portfolios.save(new Portfolio(logins.user(email).getId(), "Criticality Test " + ticker));
        holdings.save(new Holding(p.getId(), ticker, new BigDecimal("100")));
        return p;
    }

    private String currentOwner;

    private void analyse(Portfolio p, String ticker) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/analysis", p.getId())
                        .header("Authorization", logins.bearer(currentOwner)))
                .andExpect(status().isAccepted());
        for (int i = 0; i < 400; i++) {
            if (holdings.findByPortfolioIdOrderByTickerAsc(p.getId()).getFirst().getStatus() == HoldingStatus.DONE) return;
            Thread.sleep(25);
        }
        org.assertj.core.api.Assertions.assertThat(holdings.findByPortfolioIdOrderByTickerAsc(p.getId())
                .getFirst().getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    private org.springframework.test.web.servlet.ResultActions relationships(Portfolio p) throws Exception {
        return mockMvc.perform(get("/api/portfolios/{id}/relationships", p.getId())
                .header("Authorization", logins.bearer(currentOwner)));
    }

    private static Map<String, Object> item(String company, String quote, String criticality, String criticalityQuote) {
        return Map.of("company", company, "alsoKnownAs", "", "relationship", "SUPPLIER", "provides", "components",
                "quote", quote, "criticality", criticality, "criticalityQuote", criticalityQuote);
    }

    private static String answer(List<Map<String, Object>> items) throws Exception {
        String text = JsonMapper.builder().build().writeValueAsString(items);
        return JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}
