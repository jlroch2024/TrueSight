package com.truesight.portfolio;

import com.truesight.company.CompanyRepository;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.IntegrationTest;
import com.truesight.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AnnualReportAnalysisIT {

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUser currentUser;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;

    @MockitoBean SecClient sec;

    @BeforeEach
    void clearSavedReportsForSampleCompanies() {
        companies.findByCik("0001045810").ifPresent(company -> reports.deleteByCompanyId(company.getId()));
        companies.findByCik("0000937966").ifPresent(company -> reports.deleteByCompanyId(company.getId()));
    }

    @Test
    void analysisFindsNvidia10KAndAsml20FAndShowsTheirReports() throws Exception {
        Portfolio portfolio = portfolioWith("NVDA", "ASML");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        mockCompanyAndReport("ASML", "ASML Holding N.V.", "0000937966", "20-F", "0001628280-26-011378", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm");

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 2, HoldingStatus.DONE);

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("ASML Holding N.V."))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].reportType").value("20-F"))
                .andExpect(jsonPath("$[0].filingDate").value("2026-02-25"))
                .andExpect(jsonPath("$[0].reportUrl").value("https://www.sec.gov/Archives/edgar/data/937966/000162828026011378/asml-20251231.htm"))
                .andExpect(jsonPath("$[1].companyName").value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[1].reportType").value("10-K"))
                .andExpect(jsonPath("$[1].filingDate").value("2026-02-25"));

        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        Long asmlId = companies.findByCik("0000937966").orElseThrow().getId();
        assertThat(reports.findFirstByCompanyIdOrderByFilingDateDesc(nvidiaId).orElseThrow().getForm()).isEqualTo("10-K");
        assertThat(reports.findFirstByCompanyIdOrderByFilingDateDesc(asmlId).orElseThrow().getForm()).isEqualTo("20-F");
    }

    @Test
    void anUnknownTickerShowsNoReportFound() throws Exception {
        Portfolio portfolio = portfolioWith("UNKNOWN");
        when(sec.companyForTicker("UNKNOWN")).thenReturn(null);

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()))
                .andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.NO_REPORT_FOUND);
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()))
                .andExpect(jsonPath("$[0].status").value("NO_REPORT_FOUND"));
    }

    @Test
    void pressingAnalyseAgainReusesTheSavedCompanyReport() throws Exception {
        Portfolio first = portfolioWith("NVDA");
        mockCompanyAndReport("NVDA", "NVIDIA Corporation", "0001045810", "10-K", "0001045810-26-000021", "2026-02-25",
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", first.getId())).andExpect(status().isAccepted());
        awaitStatuses(first.getId(), 1, HoldingStatus.DONE);

        Portfolio second = portfolioWith("NVDA");
        mockMvc.perform(post("/api/portfolios/{id}/analysis", second.getId())).andExpect(status().isAccepted());
        awaitStatuses(second.getId(), 1, HoldingStatus.DONE);

        verify(sec, times(1)).download("https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm");
        Long nvidiaId = companies.findByCik("0001045810").orElseThrow().getId();
        assertThat(reports.findAll().stream().filter(report -> report.getCompanyId().equals(nvidiaId))).hasSize(1);
    }

    @Test
    void secFailureIsSavedAndBlocksAnotherAnalyseRequest() throws Exception {
        Portfolio portfolio = portfolioWith("NVDA");
        when(sec.companyForTicker("NVDA")).thenThrow(new SecClient.SecAccessException("The SEC could not be reached.", new RuntimeException()));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())).andExpect(status().isAccepted());
        awaitStatuses(portfolio.getId(), 1, HoldingStatus.FAILED);
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].statusReason").value("The SEC could not be reached."));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()))
                .andExpect(status().isConflict());
        verify(sec, times(1)).companyForTicker("NVDA");
    }

    private Portfolio portfolioWith(String... tickers) {
        Portfolio portfolio = portfolios.save(new Portfolio(currentUser.id(), "Report Test " + System.nanoTime()));
        for (String ticker : tickers) holdings.save(new Holding(portfolio.getId(), ticker, new BigDecimal("10")));
        return portfolio;
    }

    private void mockCompanyAndReport(String ticker, String name, String cik, String form, String accession, String filingDate, String url) {
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany(name, cik, ticker));
        when(sec.latestAnnualFiling(cik)).thenReturn(new SecClient.SecFiling(form, accession, LocalDate.parse(filingDate), url));
        when(sec.download(url)).thenReturn("<html><body><h1>Business</h1><p>Report text.</p></body></html>");
    }

    private void awaitStatuses(Long portfolioId, int count, HoldingStatus status) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<Holding> rows = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId);
            if (rows.size() == count && rows.stream().allMatch(row -> row.getStatus() == status)) return;
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId))
                .hasSize(count).allMatch(row -> row.getStatus() == status);
    }
}
