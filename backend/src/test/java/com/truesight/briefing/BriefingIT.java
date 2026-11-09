package com.truesight.briefing;

import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.news.Alert;
import com.truesight.news.AlertHolding;
import com.truesight.news.AlertHoldingRepository;
import com.truesight.news.AlertRepository;
import com.truesight.news.AlertStatus;
import com.truesight.news.Confidence;
import com.truesight.news.EventType;
import com.truesight.news.NewsArticle;
import com.truesight.news.NewsArticleRepository;
import com.truesight.news.NewsAssessment;
import com.truesight.news.NewsAssessmentRepository;
import com.truesight.news.Recommendation;
import com.truesight.news.RecommendationRepository;
import com.truesight.news.RecommendationStatus;
import com.truesight.news.Severity;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Criticality;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Concentration;
import com.truesight.report.ConcentrationKind;
import com.truesight.report.ConcentrationMeasure;
import com.truesight.report.ConcentrationRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The executive briefing PDF, against a real database: one test per Acceptance Criterion, plus the rules its
 * Technical Notes add. Each test reads the PDF back with OpenPDF's text extraction.
 *
 * <p>The portfolio holds two companies, 40% and 60%. A High alert (a production stop, 40% of the portfolio) reaches the
 * first through a supplier the report names as a sole source; a Low alert reaches the second. The headlines are real
 * ones from docs/examples/google-news-tsmc.xml; the rest is set by the test, and every test makes its own companies
 * with a unique suffix, so what other tests leave in the shared database never reaches this portfolio.
 */
@IntegrationTest
class BriefingIT {

    /** AMD's own words about its sole suppliers, from its 10-K in docs/examples. */
    private static final String SOLE_SUPPLIER = "Certain equipment and materials that are used in the manufacture of "
            + "our products are available only from a limited number of suppliers, or in some cases, a sole supplier.";
    private static final String EVIDENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";
    /** Headlines from the Google News sample captured 14 October 2026 (docs/examples/google-news-tsmc.xml). */
    private static final String HIGH_HEADLINE =
            "Taiwan Semiconductor Manufacturing Just Dropped a Major Bombshell Investors Must Be Aware Of";
    private static final String LOW_HEADLINE =
            "TSMC expected to report sixth straight quarter of record profit thanks to AI boom";
    private static final String NEXT_STEP = "Check the supplier delivery updates.";

    @Autowired MockMvc mockMvc;
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
    @Autowired RecommendationRepository recommendations;
    @Autowired ConcentrationRepository concentrations;

    private String suffix;
    private String email;
    // The High alert's next step, approved by seedPortfolio.
    private Recommendation approvedNextStep;

    @BeforeEach
    void startFresh() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "briefing-" + UUID.randomUUID() + "@example.com";
    }

    // ---- 1. Named after the portfolio and the date, with the six sections in order ----------------------------------

    @Test
    void theBriefingIsNamedAfterThePortfolioAndTheDateAndHasTheSixSectionsInOrder() throws Exception {
        Portfolio portfolio = seedPortfolio();

        MvcResult result = briefing(portfolio, email, "{\"graphPng\":\"" + dataUrl(png(40, 20)) + "\"}")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Briefing-Test-Briefing-" + LocalDate.now(ZoneOffset.UTC) + ".pdf\""))
                .andReturn();

        List<String> pages = pages(result.getResponse().getContentAsByteArray());
        List<String> sections = List.of("Prepared With TrueSight", "Supply Chain", "Holdings", "Open Alerts",
                "Sole- and Single-Source Dependencies", "About These Sources");
        List<Integer> firstPages = new ArrayList<>();
        for (String section : sections) {
            firstPages.add(firstPageWith(pages, section));
        }
        assertThat(firstPages).doesNotContain(-1).isSorted();
        assertThat(firstPages).doesNotHaveDuplicates();
        // The cover names the portfolio and the day.
        assertThat(pages.get(0)).contains("Briefing Test");
        assertThat(pages.get(0)).contains(BriefingText.date(LocalDate.now(ZoneOffset.UTC)));
    }

    @Test
    void theCoverAndTheFileNameNameTheDayInTheReadersTimeZone() throws Exception {
        Portfolio portfolio = seedPortfolio();
        // UTC+14 and UTC-11 are 25 hours apart, so they never name the same day.
        for (ZoneId zone : List.of(ZoneId.of("Pacific/Kiritimati"), ZoneId.of("Pacific/Pago_Pago"))) {
            MvcResult result = briefing(portfolio, email, "{\"graphPng\":null,\"timeZone\":\"" + zone + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"Briefing-Test-Briefing-" + LocalDate.now(zone) + ".pdf\""))
                    .andReturn();

            assertThat(pages(result.getResponse().getContentAsByteArray()).get(0))
                    .contains(BriefingText.date(LocalDate.now(zone)));
        }
    }

    @Test
    void aTimeZoneThatIsNotRealFallsBackToUtc() throws Exception {
        Portfolio portfolio = seedPortfolio();

        briefing(portfolio, email, "{\"graphPng\":null,\"timeZone\":\"Not/AZone\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Briefing-Test-Briefing-" + LocalDate.now(ZoneOffset.UTC) + ".pdf\""));
    }

    // ---- 2. The graph in the PDF is the graph as shown when the button was pressed ----------------------------------

    @Test
    void theGraphPictureSentWithTheRequestIsTheImageInTheBriefing() throws Exception {
        Portfolio portfolio = seedPortfolio();

        byte[] pdf = briefing(portfolio, email, "{\"graphPng\":\"" + dataUrl(png(40, 20)) + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(imageSizesOnPage(pdf, 2)).containsExactly("40x20");
    }

    @Test
    void aPortfolioWithNoGraphSaysNoSupplyChainYetAndStillHasEverySection() throws Exception {
        Portfolio portfolio = seedPortfolio();

        byte[] pdf = briefing(portfolio, email, "{\"graphPng\":null}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> pages = pages(pdf);
        assertThat(pages.get(1)).contains("Supply Chain").contains("No Supply Chain Yet");
        assertThat(imageSizesOnPage(pdf, 2)).isEmpty();
        assertThat(firstPageWith(pages, "About These Sources")).isGreaterThan(firstPageWith(pages, "Open Alerts"));
    }

    @Test
    void aPictureThatIsNotAnImageStillGivesABriefingWithoutAGraph() throws Exception {
        Portfolio portfolio = seedPortfolio();

        byte[] pdf = briefing(portfolio, email, "{\"graphPng\":\"this is not a picture!!\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(pages(pdf).get(1)).contains("No Supply Chain Yet");
        assertThat(imageSizesOnPage(pdf, 2)).isEmpty();
    }

    @Test
    void aPictureClaimingMoreThanTwentyMillionPixelsIsLeftOutWithoutBeingDecoded() throws Exception {
        Portfolio portfolio = seedPortfolio();

        byte[] pdf = briefing(portfolio, email, "{\"graphPng\":\"" + dataUrl(png(4500, 4500)) + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(pages(pdf).get(1)).contains("No Supply Chain Yet");
        assertThat(imageSizesOnPage(pdf, 2)).isEmpty();
    }

    // ---- 3. Alerts, exposure, next steps and dependencies match what the app shows -----------------------------------

    @Test
    void theAlertsExposureNextStepsAndDependenciesReadAsTheAppShowsThem() throws Exception {
        Portfolio portfolio = seedPortfolio();
        String shownAlerts = mockMvc.perform(get("/api/portfolios/{id}/alerts", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andReturn().getResponse().getContentAsString();
        String shownRelationships = mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(jsonPath("$[?(@.criticality == 'SOLE_SOURCE')].criticalityEvidence").value(SOLE_SUPPLIER))
                .andReturn().getResponse().getContentAsString();

        String text = text(pdf(portfolio));

        // The alerts section: the High alert before the Low one, as the alert list orders them.
        assertThat(text).contains(HIGH_HEADLINE).contains(LOW_HEADLINE);
        assertThat(text.indexOf(HIGH_HEADLINE)).isLessThan(text.indexOf(LOW_HEADLINE));
        assertThat(text).contains("HIGH").contains("Production Stop").contains("Medium Confidence");
        // Exposure and reach, as the alert card words them.
        assertThat(text).contains("40% of Portfolio");
        assertThat(text).contains("1 Holding Reached (1 Direct)");
        // The path, in the names the alert list sends.
        String supplier = jsonString(shownAlerts, "$.alerts[0].holdings[0].supplierName");
        String customer = jsonString(shownAlerts, "$.alerts[0].holdings[0].customerName");
        assertThat(text).contains(supplier + " supplies " + customer);
        // Only the analyst's approved next step.
        assertThat(text).contains("Suggested Next Step").contains(NEXT_STEP).contains("Reviewed by an Analyst");
        // The dependency, with the report's own sentence.
        assertThat(text).contains("SOLE SOURCE");
        assertThat(text).contains(SOLE_SUPPLIER);
        String criticalCounterparty = jsonString(shownRelationships, "$[?(@.criticality == 'SOLE_SOURCE')].counterparty.displayName");
        assertThat(text).contains(criticalCounterparty + " supplies");
    }

    @Test
    void theBriefingListsEachHoldingsBiggestNamedDependencyWithItsYearAndFiling() throws Exception {
        Portfolio portfolio = seedPortfolio();
        Holding held = holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(held.getCompanyId()).orElseThrow();
        Company party = company("Largest Customer Party");
        concentrations.save(new Concentration(report.getId(), held.getCompanyId(), party.getId(), "Party", ConcentrationKind.CUSTOMER,
                ConcentrationMeasure.REVENUE, new BigDecimal("0.9100"), null, LocalDate.parse("2026-03-28")));

        String text = text(pdf(portfolio));

        assertThat(text).contains("Biggest Dependencies").contains("91% of Revenue, Fiscal 2026").contains(held.getTicker() + ":");
    }

    @Test
    void aBriefingWithNoFiguresHasNoBiggestDependenciesSection() throws Exception {
        assertThat(text(pdf(seedPortfolio()))).doesNotContain("Biggest Dependencies");
    }

    @Test
    void theHoldingsAreListedLargestWeightFirstWithTheirReportAndFilingDate() throws Exception {
        Portfolio portfolio = seedPortfolio();

        String text = text(pdf(portfolio));

        assertThat(text).contains("Ticker").contains("Company").contains("Weight").contains("Report").contains("Filed");
        assertThat(text.indexOf("BBB")).isLessThan(text.indexOf("AAA"));
        assertThat(text).contains("60%").contains("40%").contains("10-K").contains("Feb 25, 2026");
    }

    @Test
    void aNextStepNobodyHasApprovedIsLeftOut() throws Exception {
        Portfolio portfolio = seedPortfolio();
        approvedNextStep.setStatus(RecommendationStatus.PENDING);
        recommendations.save(approvedNextStep);

        String text = text(pdf(portfolio));

        assertThat(text).doesNotContain("Suggested Next Step").doesNotContain(NEXT_STEP);
    }

    @Test
    void exposureIsACountOfHoldingsWhenNoHoldingHasAWeight() throws Exception {
        Portfolio portfolio = seedPortfolio();
        for (Holding holding : holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId())) {
            holding.setWeight(null);
            holdings.save(holding);
        }

        String text = text(pdf(portfolio));

        assertThat(text).contains("Weight Not Provided").doesNotContain("40% of Portfolio");
        assertThat(text).contains("of 2 Holdings");
    }

    // ---- 4. A portfolio with no alerts says so -------------------------------------------------------------------

    @Test
    void aPortfolioWithNoAlertsSaysNoOpenAlerts() throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Quiet"));
        Company company = company("Quiet Holding");
        hold(portfolio, "QQQ", company, "100");

        String text = text(pdf(portfolio));

        assertThat(text).contains("Open Alerts").contains("No open alerts.");
        assertThat(text).contains("No sole- or single-source dependencies.");
    }

    @Test
    void aDismissedAlertIsNotAnOpenAlert() throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Dismissed"));
        Company company = company("Dismissed Holding");
        Holding holding = hold(portfolio, "DDD", company, "100");
        Company supplier = company("Dismissed Supplier");
        Relationship link = relationship(company, supplier, null);
        Alert dismissed = alertAbout(portfolio, supplier, LOW_HEADLINE, Severity.LOW, "100");
        dismissed.setStatus(AlertStatus.DISMISSED);
        alerts.save(dismissed);
        alertHoldings.save(new AlertHolding(dismissed.getId(), holding.getId(), link.getId()));

        String text = text(pdf(portfolio));

        assertThat(text).contains("No open alerts.").doesNotContain(LOW_HEADLINE);
    }

    // ---- 5. Another user's portfolio answers 404 --------------------------------------------------------------------

    @Test
    void anotherUsersPortfolioIsNotFound() throws Exception {
        Portfolio portfolio = seedPortfolio();

        briefing(portfolio, "someone-else-" + UUID.randomUUID() + "@example.com", "{\"graphPng\":null}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
    }

    @Test
    void aPortfolioThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/briefing", Long.MAX_VALUE)
                        .header("Authorization", logins.bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"graphPng\":null}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
    }

    @Test
    void aBriefingNeedsALogin() throws Exception {
        Portfolio portfolio = seedPortfolio();

        mockMvc.perform(post("/api/portfolios/{id}/briefing", portfolio.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"graphPng\":null}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- The portfolio ----------------------------------------------------------------------------------------------

    /**
     * "Briefing Test": AAA (40%) and BBB (60%), each with a 10-K filed 25 February 2026. A supplier of AAA is a sole
     * source. A High alert about that supplier reaches AAA, with an approved next step; a Low alert about another
     * company reaches BBB.
     */
    private Portfolio seedPortfolio() {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Briefing Test"));
        Company first = company("First Holding");
        Company second = company("Second Holding");
        Holding a = hold(portfolio, "AAA", first, "40");
        Holding b = hold(portfolio, "BBB", second, "60");

        Company soleSupplier = company("Sole Supplier");
        Relationship sole = relationship(first, soleSupplier, Criticality.SOLE_SOURCE);
        Alert high = alertAbout(portfolio, soleSupplier, HIGH_HEADLINE, Severity.HIGH, "40");
        alertHoldings.save(new AlertHolding(high.getId(), a.getId(), sole.getId()));
        Recommendation approved = new Recommendation(high.getArticleId(), "Original suggestion.");
        approved.setEditedText(NEXT_STEP);
        approved.setStatus(RecommendationStatus.APPROVED);
        approvedNextStep = recommendations.save(approved);

        Company otherSupplier = company("Other Supplier");
        Relationship plain = relationship(second, otherSupplier, null);
        Alert low = alertAbout(portfolio, otherSupplier, LOW_HEADLINE, Severity.LOW, "60");
        alertHoldings.save(new AlertHolding(low.getId(), b.getId(), plain.getId()));
        return portfolio;
    }

    private Company company(String name) {
        return companyDirectory.findOrCreate(name + " " + suffix, null, null, List.of());
    }

    private Holding hold(Portfolio portfolio, String ticker, Company company, String weight) {
        Holding holding = new Holding(portfolio.getId(), ticker, weight == null ? null : new BigDecimal(weight));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        reports.save(new Report(company.getId(), "10-K", "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                LocalDate.parse("2026-02-25"), "https://www.sec.gov/", "Set by the test: the report's text is not used."));
        return holdings.save(holding);
    }

    /** {@code supplier} supplies {@code company}, in a report of {@code company}'s; sole or single source if given. */
    private Relationship relationship(Company company, Company supplier, Criticality criticality) {
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId()).orElseThrow();
        Relationship relationship = new Relationship(report.getId(), company.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", EVIDENCE);
        if (criticality != null) {
            relationship.setCriticality(criticality);
            relationship.setCriticalityEvidence(SOLE_SUPPLIER);
        }
        return relationships.save(relationship);
    }

    /** An alert about {@code about}'s headline, a production stop of the given severity and exposure. */
    private Alert alertAbout(Portfolio portfolio, Company about, String headline, Severity severity, String exposure) {
        NewsArticle article = articles.save(new NewsArticle(about.getId(),
                "https://example.com/" + UUID.randomUUID(), headline, "Reuters",
                Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS)));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.PRODUCTION_STOP, severity,
                Confidence.MEDIUM, "Set by the test: production stopped.",
                "Set by the test: the headline reports a stoppage."));
        return alerts.saveAndFlush(new Alert(portfolio.getId(), article.getId(), new BigDecimal(exposure)));
    }

    // ---- Asking for a briefing and reading it back ---------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions briefing(Portfolio portfolio, String asEmail, String body)
            throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/briefing", portfolio.getId())
                .header("Authorization", logins.bearer(asEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private byte[] pdf(Portfolio portfolio) throws Exception {
        return briefing(portfolio, email, "{\"graphPng\":null}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** The text of each page, with runs of spaces and line breaks made single spaces. */
    private static List<String> pages(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page).replaceAll("\\s+", " ").trim());
            }
            return pages;
        } finally {
            reader.close();
        }
    }

    private static String text(byte[] pdf) throws IOException {
        return String.join(" ", pages(pdf));
    }

    private static int firstPageWith(List<String> pages, String text) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).contains(text)) {
                return index;
            }
        }
        return -1;
    }

    /** The width and height of each picture drawn on a page (1-based), as "40x20". */
    private static List<String> imageSizesOnPage(byte[] pdf, int page) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            List<String> sizes = new ArrayList<>();
            PdfDictionary resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
            PdfDictionary pictures = resources == null ? null : resources.getAsDict(PdfName.XOBJECT);
            if (pictures == null) {
                return sizes;
            }
            for (PdfName name : pictures.getKeys()) {
                PdfObject object = PdfReader.getPdfObject(pictures.get(name));
                if (object instanceof PdfDictionary picture && PdfName.IMAGE.equals(picture.getAsName(PdfName.SUBTYPE))) {
                    PdfNumber width = picture.getAsNumber(PdfName.WIDTH);
                    PdfNumber height = picture.getAsNumber(PdfName.HEIGHT);
                    sizes.add(width.intValue() + "x" + height.intValue());
                }
            }
            return sizes;
        } finally {
            reader.close();
        }
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String dataUrl(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private static String jsonString(String json, String path) {
        Object found = com.jayway.jsonpath.JsonPath.read(json, path);
        if (found instanceof List<?> list) {
            return String.valueOf(list.get(0));
        }
        return String.valueOf(found);
    }
}
