package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Criticality;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Disruption Simulator, against a real database: one test per Acceptance Criterion, plus the rules its Technical
 * Notes add.
 *
 * <p>The portfolio has four holdings around one supplier S: T1 buys from S directly, T2 buys from M, which buys from
 * S (two steps), T3 only supplies S (S is its customer), and T4 has nothing to do with S.
 *
 * <p>Every test makes its own companies, with a unique suffix, so relationships other tests leave in the shared
 * database never reach this portfolio.
 */
@IntegrationTest
class WhatIfIT {

    /** AMD's own words about its sole suppliers, from its 10-K in docs/examples. */
    private static final String SOLE_SUPPLIER = "Certain equipment and materials that are used in the manufacture of "
            + "our products are available only from a limited number of suppliers, or in some cases, a sole supplier.";
    private static final String EVIDENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    private String suffix;
    private String email;
    private Company stopped;
    private Company middle;
    private Company t1;
    private Company t2;
    private Company t3;
    private Company t4;
    private Relationship sToT1;
    private Relationship sToMiddle;
    private Relationship middleToT2;
    private Relationship t3ToS;

    @BeforeEach
    void buildTheGraph() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "what-if-" + UUID.randomUUID() + "@example.com";
        stopped = company("Supplier S");
        middle = company("Middle M");
        t1 = company("Holding One");
        t2 = company("Holding Two");
        t3 = company("Holding Three");
        t4 = company("Holding Four");
        sToT1 = relationship(t1, stopped, RelationshipType.SUPPLIER);
        sToMiddle = relationship(middle, stopped, RelationshipType.SUPPLIER);
        middleToT2 = relationship(t2, middle, RelationshipType.SUPPLIER);
        t3ToS = relationship(t3, stopped, RelationshipType.CUSTOMER);
    }

    @Test
    void aSupplierListsEveryHoldingItReachesDirectAndTwoStepEachWithItsPath() throws Exception {
        Portfolio portfolio = portfolio(email);
        Holding one = hold(portfolio, "T1", t1, "30");
        Holding two = hold(portfolio, "T2", t2, "12.5");
        hold(portfolio, "T3", t3, "20");
        hold(portfolio, "T4", t4, "37.5");

        whatIf(portfolio, stopped, email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(stopped.getId()))
                .andExpect(jsonPath("$.holdings", hasSize(2)))
                .andExpect(jsonPath("$.holdings[0].holdingId").value(one.getId()))
                .andExpect(jsonPath("$.holdings[0].ticker").value("T1"))
                .andExpect(jsonPath("$.holdings[0].indirect").value(false))
                .andExpect(jsonPath("$.holdings[0].path", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].path[0]").value(sToT1.getId()))
                .andExpect(jsonPath("$.holdings[1].holdingId").value(two.getId()))
                .andExpect(jsonPath("$.holdings[1].indirect").value(true))
                .andExpect(jsonPath("$.holdings[1].path", hasSize(2)))
                .andExpect(jsonPath("$.holdings[1].path[0]").value(sToMiddle.getId()))
                .andExpect(jsonPath("$.holdings[1].path[1]").value(middleToT2.getId()));
    }

    @Test
    void aHoldingThatOnlySuppliesTheCompanyIsNotListed() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T3", t3, "50");
        hold(portfolio, "T4", t4, "50");

        whatIf(portfolio, stopped, email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings", hasSize(0)))
                .andExpect(jsonPath("$.exposure").value(0));
    }

    @Test
    void theExposureIsTheTotalWeightOfTheHoldingsReached() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        hold(portfolio, "T2", t2, "12.5");
        hold(portfolio, "T3", t3, "20");
        hold(portfolio, "T4", t4, "37.5");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.weighted").value(true))
                .andExpect(jsonPath("$.exposure").value(42.5))
                .andExpect(jsonPath("$.holdingCount").value(4));
    }

    @Test
    void withoutWeightsTheExposureIsHowManyHoldingsAreReachedOutOfAll() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, null);
        hold(portfolio, "T2", t2, null);
        hold(portfolio, "T3", t3, null);
        hold(portfolio, "T4", t4, null);

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.weighted").value(false))
                .andExpect(jsonPath("$.exposure").value(2))
                .andExpect(jsonPath("$.holdingCount").value(4));
    }

    @Test
    void soleAndSingleSourceLinksOnThePathsAreFlaggedWithTheirSentences() throws Exception {
        sToT1.setCriticality(Criticality.SOLE_SOURCE);
        sToT1.setCriticalityEvidence(SOLE_SUPPLIER);
        relationships.save(sToT1);
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        hold(portfolio, "T2", t2, "12.5");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.criticalLinks", hasSize(1)))
                .andExpect(jsonPath("$.criticalLinks[0].id").value(sToT1.getId()))
                .andExpect(jsonPath("$.criticalLinks[0].criticality").value("SOLE_SOURCE"))
                .andExpect(jsonPath("$.criticalLinks[0].criticalityEvidence").value(SOLE_SUPPLIER))
                .andExpect(jsonPath("$.criticalLinks[0].report.url").value("https://www.sec.gov/"));
    }

    @Test
    void aSourceLinkOffThePathsIsNotFlagged() throws Exception {
        t3ToS.setCriticality(Criticality.SINGLE_SOURCE);
        t3ToS.setCriticalityEvidence(SOLE_SUPPLIER);
        relationships.save(t3ToS);
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        hold(portfolio, "T3", t3, "20");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.criticalLinks", hasSize(0)));
    }

    @Test
    void everyLinkOnThePathsCarriesItsReviewStatusAndConfidence() throws Exception {
        sToMiddle.setReviewStatus(ReviewStatus.APPROVED);
        relationships.save(sToMiddle);
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        hold(portfolio, "T2", t2, "12.5");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.links", hasSize(3)))
                .andExpect(jsonPath("$.links[*].id",
                        containsInAnyOrder(sToT1.getId().intValue(), sToMiddle.getId().intValue(),
                                middleToT2.getId().intValue())))
                .andExpect(jsonPath("$.links[?(@.id == " + sToMiddle.getId() + ")].reviewStatus")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.links[?(@.id == " + sToT1.getId() + ")].reviewStatus")
                        .value("UNREVIEWED"))
                .andExpect(jsonPath("$.links[?(@.id == " + sToT1.getId() + ")].confidence.level").exists());
    }

    @Test
    void aRejectedRelationshipIsLeftOut() throws Exception {
        sToT1.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(sToT1);
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        hold(portfolio, "T2", t2, "12.5");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].ticker").value("T2"));
    }

    @Test
    void theStoppedCompanyIsReachedWhenItIsAHoldingWithNoRelationshipOnItsPath() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");

        whatIf(portfolio, t1, email)
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].ticker").value("T1"))
                .andExpect(jsonPath("$.holdings[0].path", hasSize(0)))
                .andExpect(jsonPath("$.exposure").value(30));
    }

    @Test
    void everyResultStatesItsAssumptions() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.assumptions").value("Assumes " + stopped.getName()
                        + " stops supplying entirely. Uses relationships from the latest annual reports; rejected "
                        + "ones are left out. Weights from your CSV."));
    }

    @Test
    void aResultWithoutWeightsSaysSoInItsAssumptions() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, null);

        whatIf(portfolio, stopped, email)
                .andExpect(jsonPath("$.assumptions").value(
                        org.hamcrest.Matchers.endsWith("This portfolio has no weights, so exposure counts holdings.")));
    }

    @Test
    void aCompanyReachingNoHoldingStillStatesItsAssumptionsAndListsNothing() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T4", t4, "100");

        whatIf(portfolio, stopped, email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings", hasSize(0)))
                .andExpect(jsonPath("$.criticalLinks", hasSize(0)))
                .andExpect(jsonPath("$.assumptions", startsWith("Assumes ")));
    }

    @Test
    void anotherUsersPortfolioAnswersNotFound() throws Exception {
        Portfolio portfolio = portfolio(email);
        hold(portfolio, "T1", t1, "30");
        String other = "what-if-other-" + UUID.randomUUID() + "@example.com";
        logins.user(other);

        whatIf(portfolio, stopped, other)
                .andExpect(status().isNotFound());
    }

    @Test
    void aCompanyThatDoesNotExistAnswersNotFound() throws Exception {
        Portfolio portfolio = portfolio(email);

        mockMvc.perform(get("/api/portfolios/{id}/what-if", portfolio.getId())
                        .param("companyId", String.valueOf(Long.MAX_VALUE))
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isNotFound());
    }

    private ResultActions whatIf(Portfolio portfolio, Company company, String asEmail) throws Exception {
        return mockMvc.perform(get("/api/portfolios/{id}/what-if", portfolio.getId())
                .param("companyId", String.valueOf(company.getId()))
                .header("Authorization", logins.bearer(asEmail)));
    }

    private Company company(String name) {
        return companyDirectory.findOrCreate(name + " What If " + suffix, null, null, List.of());
    }

    private Portfolio portfolio(String ownerEmail) {
        return portfolios.save(new Portfolio(logins.user(ownerEmail).getId(), "What If"));
    }

    /** An analysed holding of this company, with this weight (or none). */
    private Holding hold(Portfolio portfolio, String ticker, Company company, String weight) {
        Holding holding = new Holding(portfolio.getId(), ticker, weight == null ? null : new BigDecimal(weight));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        return holdings.save(holding);
    }

    /** A relationship from {@code company}'s own report, naming {@code counterparty}. */
    private Relationship relationship(Company company, Company counterparty, RelationshipType type) {
        String accession = "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Report report = reports.save(new Report(company.getId(), "10-K", accession, LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/", "Set by the test: the report's text is not used."));
        return relationships.save(new Relationship(report.getId(), company.getId(), counterparty.getId(), type,
                "semiconductor wafers", EVIDENCE));
    }
}
