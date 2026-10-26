package com.truesight.company;

import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The relationships and holdings a portfolio's pages read carry each company's display name beside its stored name,
 * through the whole app and a real database.
 *
 * <p>NVIDIA and TSMC are stored as the other integration tests store them, and NVIDIA's reports are replaced by its
 * real 10-K, as {@code FindSuppliersAndCustomersIT} does, so the result never depends on which test ran first. The
 * evidence is the real sentence from that 10-K in {@code docs/examples/relationships.json}.
 */
@IntegrationTest
class CompanyDisplayNamesIT {

    private static final String NVIDIA_CIK = "0001045810";
    private static final String TSMC_CIK = "0001046179";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;

    @Test
    void relationshipsAndHoldingsCarryEachCompanysDisplayName() throws Exception {
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", NVIDIA_CIK, "NVDA", List.of());
        nvidia.setName("NVIDIA Corporation");
        companies.save(nvidia);
        Company tsmc = companyDirectory.findOrCreate("Taiwan Semiconductor Manufacturing Company Limited", TSMC_CIK,
                "TSM", List.of());
        reports.deleteByCompanyId(nvidia.getId()); // and, with it, any relationships found in it before
        Report report = reports.save(new Report(nvidia.getId(), "10-K", "0001045810-26-000021",
                LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/nvda-20260125.htm",
                Examples.nvidia10K()));
        Relationship relationship = relationships.save(new Relationship(report.getId(), nvidia.getId(), tsmc.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers",
                "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung "
                        + "Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers."));

        String email = "display-names-" + UUID.randomUUID() + "@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Display Names"));
        Holding holding = new Holding(portfolio.getId(), "NVDA", new BigDecimal("100"));
        holding.setCompanyId(nvidia.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].company.name", relationship.getId()).value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[?(@.id == %d)].company.displayName", relationship.getId()).value("NVIDIA"))
                .andExpect(jsonPath("$[?(@.id == %d)].counterparty.name", relationship.getId())
                        .value("Taiwan Semiconductor Manufacturing Company Limited"))
                .andExpect(jsonPath("$[?(@.id == %d)].counterparty.displayName", relationship.getId())
                        .value("Taiwan Semiconductor Manufacturing"));

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("NVIDIA Corporation"))
                .andExpect(jsonPath("$[0].companyDisplayName").value("NVIDIA"));
    }

    @Test
    void aHoldingNotYetMatchedToACompanyHasNoDisplayName() throws Exception {
        String email = "display-names-" + UUID.randomUUID() + "@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Not Analysed"));
        holdings.save(new Holding(portfolio.getId(), "NVDA", null));

        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").doesNotExist())
                .andExpect(jsonPath("$[0].companyDisplayName").doesNotExist());
    }
}
