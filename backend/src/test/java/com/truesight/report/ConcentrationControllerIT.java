package com.truesight.report;

import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/companies/{companyId}/concentrations. The rows are saved straight into the tables, with the values of
 * Cirrus Logic's real figures ({@code docs/examples/crus-20260328_htm.xml}): Apple's revenue share and the ten
 * largest customers'.
 */
@IntegrationTest
class ConcentrationControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired ConcentrationRepository concentrations;

    private Company filer;
    private Company apple;

    @AfterEach
    void removeWhatTheTestSaved() {
        if (filer != null) companies.delete(filer);
        if (apple != null) companies.delete(apple);
    }

    private void save() {
        filer = companies.save(new Company("Cirrus Test " + UUID.randomUUID(), null, null));
        apple = companies.save(new Company("Apple Test " + UUID.randomUUID(), null, "AAPLX"));
        Report report = reports.save(new Report(filer.getId(), "10-K", "ctrl-" + System.nanoTime(),
                LocalDate.parse("2026-05-21"), "https://www.sec.gov/Archives/edgar/data/772406/x/crus-20260328.htm",
                "Text."));
        LocalDate end = LocalDate.parse("2026-03-28");
        concentrations.save(new Concentration(report.getId(), filer.getId(), apple.getId(), "Apple, Inc.",
                ConcentrationKind.CUSTOMER, ConcentrationMeasure.REVENUE, new BigDecimal("0.8700"),
                LocalDate.parse("2023-04-02"), LocalDate.parse("2024-03-30")));
        concentrations.save(new Concentration(report.getId(), filer.getId(), apple.getId(), "Apple, Inc.",
                ConcentrationKind.CUSTOMER, ConcentrationMeasure.REVENUE, new BigDecimal("0.9100"),
                LocalDate.parse("2025-03-30"), end));
        concentrations.save(new Concentration(report.getId(), filer.getId(), null, "Ten Largest Customers",
                ConcentrationKind.CUSTOMER, ConcentrationMeasure.REVENUE, new BigDecimal("0.9600"),
                LocalDate.parse("2025-03-30"), end));
    }

    @Test
    void listsTheCompanysFiguresNewestPeriodFirstWithTheirPartyAndReport() throws Exception {
        save();

        mockMvc.perform(get("/api/companies/{id}/concentrations", filer.getId())
                        .header("Authorization", logins.bearer("concentrations-list@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-03-28"))
                .andExpect(jsonPath("$[2].periodEnd").value("2024-03-30"))
                .andExpect(jsonPath("$[2].label").value("Apple, Inc."))
                .andExpect(jsonPath("$[2].share").value(0.87))
                .andExpect(jsonPath("$[2].kind").value("CUSTOMER"))
                .andExpect(jsonPath("$[2].measure").value("REVENUE"))
                .andExpect(jsonPath("$[2].periodStart").value("2023-04-02"))
                .andExpect(jsonPath("$[2].counterparty.id").value(apple.getId()))
                .andExpect(jsonPath("$[2].counterparty.name").value(apple.getName()))
                .andExpect(jsonPath("$[2].counterparty.ticker").value("AAPLX"))
                .andExpect(jsonPath("$[2].counterparty.displayName").exists())
                .andExpect(jsonPath("$[2].report.form").value("10-K"))
                .andExpect(jsonPath("$[2].report.filingDate").value("2026-05-21"))
                .andExpect(jsonPath("$[2].report.url").value(
                        "https://www.sec.gov/Archives/edgar/data/772406/x/crus-20260328.htm"));
    }

    @Test
    void anUnnamedPartyHasNoCounterparty() throws Exception {
        save();

        mockMvc.perform(get("/api/companies/{id}/concentrations", filer.getId())
                        .header("Authorization", logins.bearer("concentrations-unnamed@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'Ten Largest Customers')].counterparty").value(
                        org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void aCompanyWithNoFiguresGivesAnEmptyList() throws Exception {
        filer = companies.save(new Company("Empty Test " + UUID.randomUUID(), null, null));

        mockMvc.perform(get("/api/companies/{id}/concentrations", filer.getId())
                        .header("Authorization", logins.bearer("concentrations-empty@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownCompanyIsNotFound() throws Exception {
        mockMvc.perform(get("/api/companies/{id}/concentrations", 987654321L)
                        .header("Authorization", logins.bearer("concentrations-unknown@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Company not found."));
    }

    @Test
    void aRequestWithNoLoginIsRefused() throws Exception {
        mockMvc.perform(get("/api/companies/{id}/concentrations", 1L)).andExpect(status().isUnauthorized());
    }
}
