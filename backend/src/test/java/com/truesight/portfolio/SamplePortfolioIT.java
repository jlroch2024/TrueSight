package com.truesight.portfolio;

import com.jayway.jsonpath.JsonPath;
import com.truesight.relationship.GeminiClient;
import com.truesight.report.SecClient;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SamplePortfolioIT {

    private static final List<String> SAMPLE_TICKERS =
            List.of("AMD", "ASML", "AVGO", "CRUS", "LRCX", "NVDA", "QCOM", "SONY", "STM", "TSLA");

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;

    // Tests never call the SEC: no ticker is found, so each holding's analysis ends quickly as No Report Found.
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void theSecFindsNoCompany() {
        reset(sec, gemini);
        when(sec.companyForTicker(anyString())).thenReturn(null);
    }

    @Test
    void trySamplePortfolioCreatesTheTenSampleHoldingsAtTenPercentEachAndStartsTheirAnalysis() throws Exception {
        String email = "sample-portfolio-create@example.com";

        Long id = createSample(email, "Sample Portfolio");

        List<Holding> saved = holdings.findByPortfolioIdOrderByTickerAsc(id);
        assertThat(saved).extracting(Holding::getTicker).containsExactlyElementsOf(SAMPLE_TICKERS);
        assertThat(saved).allSatisfy(holding ->
                assertThat(holding.getWeight()).isEqualByComparingTo(new BigDecimal("10")));
        awaitNoneWaiting(id);
        verify(sec, atLeastOnce()).companyForTicker("NVDA");
    }

    @Test
    void aUserWhoAlreadyHasASamplePortfolioGetsSamplePortfolioTwo() throws Exception {
        String email = "sample-portfolio-second@example.com";
        createSample(email, "Sample Portfolio");

        createSample(email, "Sample Portfolio 2");
        createSample(email, "Sample Portfolio 3");
    }

    @Test
    void theSamplePortfolioCanBeRenamedAndDeletedLikeAnyOther() throws Exception {
        String email = "sample-portfolio-ordinary@example.com";
        Long id = createSample(email, "Sample Portfolio");
        awaitNoneWaiting(id);

        mockMvc.perform(put("/api/portfolios/{id}", id).header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"My Chips\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Chips"));
        mockMvc.perform(delete("/api/portfolios/{id}", id).header("Authorization", logins.bearer(email)))
                .andExpect(status().isNoContent());

        assertThat(portfolios.findById(id)).isEmpty();
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(id)).isEmpty();
    }

    @Test
    void theBackendsSampleHoldingsAreTheSameAsTheRepositorysSampleCsv() throws Exception {
        Path docs = Files.exists(Path.of("docs/examples/holdings.csv"))
                ? Path.of("docs/examples/holdings.csv") : Path.of("../docs/examples/holdings.csv");
        try (InputStream copy = PortfolioService.class.getResourceAsStream(PortfolioService.SAMPLE_HOLDINGS)) {
            assertThat(copy).isNotNull();
            assertThat(copy.readAllBytes()).isEqualTo(Files.readAllBytes(docs));
        }
    }

    @Test
    void creatingASamplePortfolioNeedsALogin() throws Exception {
        mockMvc.perform(post("/api/portfolios/sample")).andExpect(status().isUnauthorized());
    }

    private Long createSample(String email, String expectedName) throws Exception {
        String body = mockMvc.perform(post("/api/portfolios/sample").header("Authorization", logins.bearer(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(expectedName))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void awaitNoneWaiting(Long portfolioId) throws InterruptedException {
        for (int attempt = 0; attempt < 400; attempt++) {
            List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
            if (rows.stream().allMatch(holding -> holding.getStatus() == HoldingStatus.NO_REPORT_FOUND)) return;
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId))
                .allSatisfy(holding -> assertThat(holding.getStatus()).isEqualTo(HoldingStatus.NO_REPORT_FOUND));
    }
}
