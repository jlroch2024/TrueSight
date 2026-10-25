package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * See What a News Event Affects, against a real database: one test per Acceptance Criterion, plus the rules the
 * Technical Notes add. Each test calls {@link AlertBuilder} directly, after building the companies, holdings and
 * relationships through the repositories and saving an article with its assessment, as Filter Out Noise would.
 *
 * <p>Every test makes its own companies, with a unique suffix, so articles and relationships other tests leave in the
 * shared database never reach these portfolios.
 */
@IntegrationTest
class AlertBuilderIT {

    /** NVIDIA's own words about TSMC, from its 10-K in docs/examples. */
    private static final String NVIDIA_ON_TSMC = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    @Autowired AlertBuilder alertBuilder;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired AlertRepository alerts;
    @Autowired AlertHoldingRepository alertHoldings;

    private String suffix;
    private Company tsmc;
    private Company nvidia;

    @BeforeEach
    void makeThisTestsOwnCompanies() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        tsmc = company("TSMC");
        nvidia = company("NVIDIA");
    }

    @Test
    void anEventAtTsmcAlertsAPortfolioHoldingNvidiaNamingNvidiaAndTheRelationship() {
        Relationship tsmcSuppliesNvidia = supplier(nvidia, tsmc);
        Portfolio portfolio = portfolio();
        Holding nvidiaHolding = hold(portfolio, nvidia, "60");
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId)
                .containsExactly(tuple(nvidiaHolding.getId(), tsmcSuppliesNvidia.getId()));
    }

    @Test
    void theSameEventCreatesNoAlertInAPortfolioWithNoLinkToTsmc() {
        supplier(nvidia, tsmc);
        Portfolio unlinked = portfolio();
        hold(unlinked, company("Unlinked"), "100");
        event(tsmc);

        alertBuilder.run(unlinked.getId());

        assertThat(alerts.findByPortfolioId(unlinked.getId())).isEmpty();
    }

    @Test
    void twoPortfoliosHoldingNvidiaAt40And5PercentGetExposuresOf40And5() {
        supplier(nvidia, tsmc);
        Portfolio large = portfolio();
        hold(large, nvidia, "40");
        Portfolio small = portfolio();
        hold(small, nvidia, "5");
        NewsArticle event = event(tsmc);

        alertBuilder.run(large.getId());
        alertBuilder.run(small.getId());

        assertThat(exposure(large, event)).isEqualByComparingTo("40");
        assertThat(exposure(small, event)).isEqualByComparingTo("5");
    }

    @Test
    void anEventReachingTwoHoldingsCreatesOneAlertListingBothWithTheirTotalWeight() {
        Company amd = company("AMD");
        Relationship forNvidia = supplier(nvidia, tsmc);
        Relationship forAmd = supplier(amd, tsmc);
        Portfolio portfolio = portfolio();
        Holding nvidiaHolding = hold(portfolio, nvidia, "30");
        Holding amdHolding = hold(portfolio, amd, "12.5");
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).hasSize(1);
        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alert.getExposure()).isEqualByComparingTo("42.5");
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId)
                .containsExactlyInAnyOrder(tuple(nvidiaHolding.getId(), forNvidia.getId()),
                        tuple(amdHolding.getId(), forAmd.getId()));
    }

    @Test
    void anEventAboutAHeldCompanyCreatesAnAlertWithThatHoldingAndNoRelationship() {
        Portfolio portfolio = portfolio();
        Holding tsmcHolding = hold(portfolio, tsmc, "25");
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alert.getExposure()).isEqualByComparingTo("25");
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId)
                .containsExactly(tuple(tsmcHolding.getId(), null));
    }

    @Test
    void aRejectedRelationshipNeverLeadsToAnAlert() {
        Relationship rejected = supplier(nvidia, tsmc);
        rejected.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(rejected);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, "60");
        event(tsmc);

        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).isEmpty();
    }

    @Test
    void anArticleAssessedAsNoiseNeverCreatesAnAlert() {
        supplier(nvidia, tsmc);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, "60");
        NewsArticle noise = article(tsmc);
        assessments.save(new NewsAssessment(noise.getId(), false, null, null, Confidence.HIGH, null,
                "Set by the test: not an event."));

        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).isEmpty();
    }

    @Test
    void checkingTheNewsAgainDoesNotCreateTheSameAlertTwice() {
        supplier(nvidia, tsmc);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, "60");
        event(tsmc);

        alertBuilder.run(portfolio.getId());
        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).hasSize(1);
    }

    @Test
    void anUnreviewedRelationshipStillLeadsToAnAlert() {
        Relationship unreviewed = supplier(nvidia, tsmc);
        assertThat(unreviewed.getReviewStatus()).isEqualTo(ReviewStatus.UNREVIEWED);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, "60");
        event(tsmc);

        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).hasSize(1);
    }

    @Test
    void whenSeveralRelationshipsLinkAHoldingTheApprovedOneIsKept() {
        supplier(nvidia, tsmc);
        Relationship approved = supplier(nvidia, tsmc);
        approved.setReviewStatus(ReviewStatus.APPROVED);
        relationships.save(approved);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, "60");
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getRelationshipId)
                .containsExactly(approved.getId());
    }

    @Test
    void anEventReachesAHoldingThroughARelationshipInTheEventCompanysOwnReport() {
        Company apple = company("Apple");
        supplier(apple, tsmc); // so TSMC's news is checked for this portfolio
        Relationship nvidiaBuysFromTsmc = relationship(tsmc, nvidia, RelationshipType.CUSTOMER);
        Portfolio portfolio = portfolio();
        hold(portfolio, apple, "50");
        Holding nvidiaHolding = hold(portfolio, nvidia, "20");
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId)
                .contains(tuple(nvidiaHolding.getId(), nvidiaBuysFromTsmc.getId()));
    }

    @Test
    void whenNoHoldingHasAWeightExposureIsTheNumberOfHoldingsReached() {
        Company amd = company("AMD");
        supplier(nvidia, tsmc);
        supplier(amd, tsmc);
        Portfolio portfolio = portfolio();
        hold(portfolio, nvidia, null);
        hold(portfolio, amd, null);
        NewsArticle event = event(tsmc);

        alertBuilder.run(portfolio.getId());

        assertThat(exposure(portfolio, event)).isEqualByComparingTo("2");
    }

    @Test
    void anEventAtASuppliersSupplierReachesTheHoldingThroughBothLinks() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        Relationship zeissSuppliesAsml = supplier(asml, zeiss);
        Relationship asmlSuppliesTsmc = supplier(tsmc, asml);
        Portfolio portfolio = portfolio();
        Holding tsmcHolding = hold(portfolio, tsmc, "35");
        NewsArticle event = event(zeiss);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId,
                        AlertHolding::getSecondRelationshipId)
                .containsExactly(tuple(tsmcHolding.getId(), zeissSuppliesAsml.getId(), asmlSuppliesTsmc.getId()));
    }

    @Test
    void aHoldingReachedDirectlyIsSavedOnceAndNeverAlsoIndirectly() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        supplier(asml, zeiss);
        supplier(tsmc, asml);
        Relationship zeissSuppliesTsmc = supplier(tsmc, zeiss);
        Portfolio portfolio = portfolio();
        Holding tsmcHolding = hold(portfolio, tsmc, "35");
        NewsArticle event = event(zeiss);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId,
                        AlertHolding::getSecondRelationshipId)
                .containsExactly(tuple(tsmcHolding.getId(), zeissSuppliesTsmc.getId(), null));
    }

    @Test
    void aRejectedLinkOnEitherStepStopsTheIndirectPath() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        Relationship first = supplier(asml, zeiss);
        Relationship second = supplier(tsmc, asml);
        first.setReviewStatus(ReviewStatus.REJECTED);
        relationships.save(first);
        Portfolio firstRejected = portfolio();
        hold(firstRejected, tsmc, "35");
        NewsArticle firstEvent = event(zeiss);

        alertBuilder.run(firstRejected.getId());
        assertThat(alerts.findByPortfolioIdAndArticleId(firstRejected.getId(), firstEvent.getId())).isEmpty();

        first.setReviewStatus(ReviewStatus.APPROVED);
        second.setReviewStatus(ReviewStatus.REJECTED);
        relationships.saveAll(List.of(first, second));
        Portfolio secondRejected = portfolio();
        hold(secondRejected, tsmc, "35");
        NewsArticle secondEvent = event(zeiss);

        alertBuilder.run(secondRejected.getId());
        assertThat(alerts.findByPortfolioIdAndArticleId(secondRejected.getId(), secondEvent.getId())).isEmpty();
    }

    @Test
    void anEventAtACustomersCustomerNeverReachesTheHolding() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        supplier(asml, tsmc);
        supplier(zeiss, asml);
        Portfolio portfolio = portfolio();
        hold(portfolio, tsmc, "35");
        event(zeiss);

        alertBuilder.run(portfolio.getId());

        assertThat(alerts.findByPortfolioId(portfolio.getId())).isEmpty();
    }

    @Test
    void directAndIndirectHoldingsBothCountInExposure() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        supplier(asml, zeiss);
        supplier(tsmc, asml);
        Portfolio portfolio = portfolio();
        Holding direct = hold(portfolio, asml, "10");
        Holding indirect = hold(portfolio, tsmc, "20");
        NewsArticle event = event(zeiss);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alert.getExposure()).isEqualByComparingTo("30");
        List<AlertHolding> reached = alertHoldings.findByAlertId(alert.getId());
        assertThat(reached).hasSize(2);
        assertThat(reached.stream().filter(found -> found.getHoldingId().equals(direct.getId())).findFirst()
                .orElseThrow().getSecondRelationshipId()).isNull();
        assertThat(reached.stream().filter(found -> found.getHoldingId().equals(indirect.getId())).findFirst()
                .orElseThrow().getSecondRelationshipId()).isNotNull();
    }

    @Test
    void approvedLinksArePreferredWhenSeveralIndirectPathsExist() {
        Company asml = company("ASML");
        Company zeiss = company("Carl Zeiss SMT");
        supplier(asml, zeiss);
        Relationship approvedFirst = supplier(asml, zeiss);
        approvedFirst.setReviewStatus(ReviewStatus.APPROVED);
        supplier(tsmc, asml);
        Relationship approvedSecond = supplier(tsmc, asml);
        approvedSecond.setReviewStatus(ReviewStatus.APPROVED);
        relationships.saveAll(List.of(approvedFirst, approvedSecond));
        Portfolio portfolio = portfolio();
        Holding holding = hold(portfolio, tsmc, "35");
        NewsArticle event = event(zeiss);

        alertBuilder.run(portfolio.getId());

        Alert alert = alerts.findByPortfolioIdAndArticleId(portfolio.getId(), event.getId()).orElseThrow();
        assertThat(alertHoldings.findByAlertId(alert.getId()))
                .extracting(AlertHolding::getHoldingId, AlertHolding::getRelationshipId,
                        AlertHolding::getSecondRelationshipId)
                .containsExactly(tuple(holding.getId(), approvedFirst.getId(), approvedSecond.getId()));
    }

    // ---- building the test data ----

    private Company company(String name) {
        return companyDirectory.findOrCreate(name + " Alert Test " + suffix, null, null, List.of());
    }

    private Portfolio portfolio() {
        String email = "alerts-" + UUID.randomUUID() + "@example.com";
        return portfolios.save(new Portfolio(logins.user(email).getId(), "Alerts"));
    }

    /** An analysed holding of this company, with this weight (or none). */
    private Holding hold(Portfolio portfolio, Company company, String weight) {
        Holding holding = new Holding(portfolio.getId(), "T" + suffix.substring(0, 4),
                weight == null ? null : new BigDecimal(weight));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        return holdings.save(holding);
    }

    /** "supplier supplies customer", as the customer's own report says it. */
    private Relationship supplier(Company customer, Company supplier) {
        return relationship(customer, supplier, RelationshipType.SUPPLIER);
    }

    private Relationship relationship(Company company, Company counterparty, RelationshipType type) {
        String accession = "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Report report = reports.save(new Report(company.getId(), "10-K", accession, LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/", "Set by the test: the report's text is not used."));
        return relationships.save(new Relationship(report.getId(), company.getId(), counterparty.getId(), type,
                "semiconductor wafers", NVIDIA_ON_TSMC));
    }

    /** A real headline from the saved Google News sample, about this company. */
    private NewsArticle article(Company company) {
        NewsItem item = new GoogleNewsParser().parse(Examples.googleNewsTsmc()).getFirst();
        return articles.save(new NewsArticle(company.getId(), item.url() + "#" + UUID.randomUUID(), item.title(),
                item.source(), item.publishedAt()));
    }

    /** An article about this company, assessed as a real event. */
    private NewsArticle event(Company company) {
        NewsArticle article = article(company);
        assessments.save(new NewsAssessment(article.getId(), true, EventType.PRODUCTION_STOP, Severity.HIGH,
                Confidence.HIGH, "Set by the test.", "Set by the test: an event."));
        return article;
    }

    private BigDecimal exposure(Portfolio portfolio, NewsArticle article) {
        return alerts.findByPortfolioIdAndArticleId(portfolio.getId(), article.getId()).orElseThrow().getExposure();
    }
}
