package com.truesight.relationship;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read Each Supplier's Own Report, criterion 7: the portfolio's graph shows a supplier's suppliers, linked to that
 * supplier. A holding's report names a supplier; the supplier's own report names its supplier and another customer.
 * The companies, reports and sentences are made up for the test, and say so.
 */
@IntegrationTest
class RelationshipsIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory directory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    private String nonce;
    private Company holding;
    private Company supplier;
    private Company suppliersSupplier;
    private Company otherCustomer;
    private Relationship holdingsSupplier;

    @BeforeEach
    void aHoldingWhoseSupplierHasItsOwnSupplierAndAnotherCustomer() {
        nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        holding = company("Holding");
        supplier = company("Supplier");
        suppliersSupplier = company("Suppliers Supplier");
        otherCustomer = company("Other Customer");
        Report holdingsReport = report(holding, "h");
        Report suppliersReport = report(supplier, "s");
        holdingsSupplier = relationships.save(new Relationship(holdingsReport.getId(), holding.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "Parts", "Test sentence: the supplier sells parts to the holding."));
        relationships.save(new Relationship(suppliersReport.getId(), supplier.getId(), suppliersSupplier.getId(),
                RelationshipType.SUPPLIER, "Materials", "Test sentence: the supplier buys materials."));
        relationships.save(new Relationship(suppliersReport.getId(), supplier.getId(), otherCustomer.getId(),
                RelationshipType.CUSTOMER, "Parts", "Test sentence: another customer buys the supplier's parts."));
    }

    @Test
    void thePortfolioShowsASuppliersSupplierLinkedToThatSupplier() throws Exception {
        String email = "relationships-two-steps@example.com";
        Portfolio portfolio = portfolioHolding(email);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].company.id").value(containsInAnyOrder(
                        holding.getId().intValue(), supplier.getId().intValue())))
                .andExpect(jsonPath("$[1].company.id").value(supplier.getId()))
                .andExpect(jsonPath("$[1].counterparty.id").value(suppliersSupplier.getId()))
                .andExpect(jsonPath("$[1].type").value("SUPPLIER"));
    }

    @Test
    void aSuppliersOtherCustomerIsNotShownBecauseItDoesNotReachThePortfolio() throws Exception {
        String email = "relationships-other-customer@example.com";
        Portfolio portfolio = portfolioHolding(email);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].counterparty.id").value(not(hasItem(otherCustomer.getId().intValue()))));
    }

    @Test
    void aRejectedSupplierBringsNoneOfItsOwnSuppliers() throws Exception {
        holdingsSupplier.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(holdingsSupplier);
        String email = "relationships-rejected-supplier@example.com";
        Portfolio portfolio = portfolioHolding(email);

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Portfolio portfolioHolding(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Two Steps " + nonce));
        Holding held = new Holding(portfolio.getId(), "TS" + nonce.substring(0, 6).toUpperCase(), new BigDecimal("10"));
        held.setCompanyId(holding.getId());
        held.setStatus(HoldingStatus.DONE);
        holdings.save(held);
        return portfolio;
    }

    private Company company(String role) {
        return directory.findOrCreate("Ts217 " + role + " " + nonce, null, null, List.of());
    }

    private Report report(Company company, String suffix) {
        return reports.save(new Report(company.getId(), "10-K", "ts217-" + suffix + "-" + nonce,
                LocalDate.parse("2026-02-25"), "https://www.sec.gov/example", "Test report text."));
    }
}
