package com.truesight.report;

import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.GeminiAnswer;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dependency Figures from the Filings, criteria 1 to 7, through a holding's real Analyse.
 *
 * <p>The holding is Cirrus Logic. The fake SEC serves its real tagged figures ({@code crus-20260328_htm.xml} and
 * {@code _lab.xml} in {@code docs/examples}), counting the downloads. {@code docs/examples} holds no Cirrus report
 * text or Gemini answer, so its report is served NVIDIA's real 10-K and Gemini's real answer for it as a stand-in:
 * only the figures are under test. Nothing here calls the SEC or Gemini.
 */
@IntegrationTest
class FigureServiceIT {

    private static final String CIK = "0000772406";
    private static final String URL =
            "https://www.sec.gov/Archives/edgar/data/772406/000077240626000018/crus-20260328.htm";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyRepository companies;
    @Autowired CompanyNameRepository nameRows;
    @Autowired ReportRepository reports;
    @MockitoSpyBean ConcentrationRepository concentrations;
    @Autowired @Qualifier("taskExecutor") ThreadPoolTaskExecutor background;
    @Autowired GeminiAnswer answers;
    @Autowired RelationshipRepository relationships;
    @Autowired SupplierReader supplierReader;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void fakeTheSecAndGemini() {
        purge();
        when(sec.companyForTicker("CRUS")).thenReturn(new SecClient.SecCompany("CIRRUS LOGIC, INC.", CIK, "CRUS"));
        when(sec.latestAnnualFiling(CIK)).thenReturn(
                new SecClient.SecFiling("10-K", "0000772406-26-000018", LocalDate.parse("2026-05-21"), URL));
        when(sec.download(URL)).thenReturn(html(Examples.nvidia10K()));
        when(sec.taggedFigures(any(Report.class))).thenReturn(
                Optional.of(new SecClient.TaggedFigures(Examples.cirrusFigures(), Examples.cirrusLabels())));
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> answer(Examples.geminiNvidia()));
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
    }

    // ---- criteria 1 and 2 ----------------------------------------------------------------------------------------

    @Test
    void analysingAHoldingStoresEachFigureWithItsPartyKindMeasureShareAndEveryPeriod() throws Exception {
        analyse("figures-stored@example.com");

        List<Concentration> rows = concentrations.findByReportIdOrderByIdAsc(reportId());
        assertThat(rows).hasSize(10).allSatisfy(row -> {
            assertThat(row.getCompanyId()).isEqualTo(cirrus().getId());
            assertThat(row.getKind()).isEqualTo(ConcentrationKind.CUSTOMER);
        });
        List<Concentration> apple = rows.stream().filter(row -> row.getLabel().equals("Apple, Inc.")).toList();
        assertThat(apple).hasSize(3).allSatisfy(row -> assertThat(row.getMeasure()).isEqualTo(ConcentrationMeasure.REVENUE));
        assertThat(apple).extracting(Concentration::getShare)
                .containsExactlyInAnyOrder(new BigDecimal("0.9100"), new BigDecimal("0.8900"), new BigDecimal("0.8700"));
        assertThat(apple).extracting(Concentration::getPeriodEnd).doesNotHaveDuplicates();
        assertThat(rows).filteredOn(row -> row.getLabel().equals("Foxconn"))
                .extracting(Concentration::getMeasure).containsOnly(ConcentrationMeasure.RECEIVABLES);
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
    }

    @Test
    void aNamedPartyIsLinkedToItsCompanyAndAnUnnamedOneIsStoredWithNone() throws Exception {
        analyse("figures-linked@example.com");

        List<Concentration> rows = concentrations.findByReportIdOrderByIdAsc(reportId());
        Company apple = nameRows.findByNameKey(CompanyDirectory.nameKey("Apple, Inc."))
                .flatMap(row -> companies.findById(row.getCompanyId())).orElseThrow();
        assertThat(rows).filteredOn(row -> row.getLabel().equals("Apple, Inc."))
                .extracting(Concentration::getCounterpartyId).containsOnly(apple.getId());
        assertThat(rows).filteredOn(row -> row.getLabel().equals("Ten Largest Customers")).hasSize(3)
                .extracting(Concentration::getCounterpartyId).containsOnlyNulls();
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void figuresOfAnotherTypeThanCustomerOrSupplierAreNotStoredButTheReportIsStillRead() throws Exception {
        when(sec.taggedFigures(any(Report.class))).thenReturn(Optional.of(new SecClient.TaggedFigures(
                Examples.cirrusFigures().replace("us-gaap:CustomerConcentrationRiskMember",
                        "us-gaap:ProductConcentrationRiskMember"),
                Examples.cirrusLabels())));

        analyse("figures-other-type@example.com");

        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).isEmpty();
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
    }

    // ---- criterion 4 ---------------------------------------------------------------------------------------------

    @Test
    void aReportsFiguresAreReadOnceWhateverPortfolioAnalysesItAgain() throws Exception {
        analyse("figures-once@example.com");
        analyse("figures-once-again@example.com");

        verify(sec, times(1)).taggedFigures(any(Report.class));
        verify(sec, times(1)).download(URL);
        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).hasSize(10);
    }

    // ---- criterion 5 ---------------------------------------------------------------------------------------------

    @Test
    void aReportWithNoTaggedFiguresIsMarkedReadAndStoresNoneAndIsNotAskedForAgain() throws Exception {
        when(sec.taggedFigures(any(Report.class))).thenReturn(Optional.empty());

        analyse("figures-none@example.com");
        analyse("figures-none-again@example.com");

        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).isEmpty();
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
        verify(sec, times(1)).taggedFigures(any(Report.class));
    }

    // ---- criterion 6 ---------------------------------------------------------------------------------------------

    @Test
    void aDoneHoldingWhoseFiguresWereNeverReadHasThemReadAtTheNextAnalyseWithNoAiCall() throws Exception {
        analyse("figures-late@example.com");
        Long reportId = reportId();
        // As if the holding had been analysed before figures were read: Done, with the figures not yet read.
        concentrations.deleteAll(concentrations.findByReportIdOrderByIdAsc(reportId));
        Report report = reports.findById(reportId).orElseThrow();
        report.setFiguresReadAt(null);
        reports.save(report);
        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.FIND_RELATIONSHIPS));

        analyse("figures-late-again@example.com");

        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId)).hasSize(10);
        assertThat(reports.findById(reportId).orElseThrow().getFiguresReadAt()).isNotNull();
        verify(gemini, times(1)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.FIND_RELATIONSHIPS));
        verify(sec, times(1)).download(URL);
    }

    // ---- criterion 7 ---------------------------------------------------------------------------------------------

    @Test
    void whenTheSecCannotBeReachedTheHoldingIsStillDoneAndTheFiguresAreTriedAtTheNextAnalyse() throws Exception {
        when(sec.taggedFigures(any(Report.class)))
                .thenThrow(new SecClient.SecAccessException("The SEC could not be reached.", null));
        Portfolio first = analyse("figures-unreachable@example.com");

        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(first.getId()).getFirst().getStatus())
                .isEqualTo(HoldingStatus.DONE);
        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).isEmpty();
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNull();

        when(sec.taggedFigures(any(Report.class))).thenReturn(
                Optional.of(new SecClient.TaggedFigures(Examples.cirrusFigures(), Examples.cirrusLabels())));
        analyse("figures-unreachable-again@example.com");

        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).hasSize(10);
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
    }

    @Test
    void aFailureSavingTheFiguresNeverUndoesTheHoldingsRelationships() throws Exception {
        doThrow(new DataIntegrityViolationException("The figures could not be saved."))
                .when(concentrations).save(any(Concentration.class));

        Portfolio portfolio = analyse("figures-save-fails@example.com");

        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(HoldingStatus.DONE);
        assertThat(relationships.findByReportIdOrderByIdAsc(reportId())).isNotEmpty();
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNull();
    }

    @Test
    void aLabelLongerThanTheColumnIsCutNotRefused() throws Exception {
        when(sec.taggedFigures(any(Report.class))).thenReturn(Optional.of(new SecClient.TaggedFigures(
                Examples.cirrusFigures(),
                Examples.cirrusLabels().replace(">Apple, Inc.</link:label>", ">Apple " + "x".repeat(400) + "</link:label>"))));

        analyse("figures-long-label@example.com");

        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).hasSize(10)
                .allSatisfy(row -> assertThat(row.getLabel().length()).isLessThanOrEqualTo(300));
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
    }

    @Test
    void figuresThatCannotBeReadAsXmlNeverFailTheHoldingAndAreNotAskedForAgain() throws Exception {
        when(sec.taggedFigures(any(Report.class)))
                .thenReturn(Optional.of(new SecClient.TaggedFigures("not xml", "not xml")));

        Portfolio portfolio = analyse("figures-unreadable@example.com");

        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(HoldingStatus.DONE);
        // Asking again would not change the files: the report is marked read with no figures.
        assertThat(concentrations.findByReportIdOrderByIdAsc(reportId())).isEmpty();
        assertThat(reports.findById(reportId()).orElseThrow().getFiguresReadAt()).isNotNull();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private Portfolio analyse(String email) throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Figures " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "CRUS", new BigDecimal("10")));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                .header("Authorization", logins.bearer(email))).andExpect(status().isAccepted());
        await(() -> holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus()
                == HoldingStatus.DONE);
        await(() -> {
            SupplierReader.Progress progress = supplierReader.progressOf(List.of(cirrus().getId()));
            return progress.toRead() > 0 && progress.read() == progress.toRead();
        });
        // The figures are read by the job after the holding is Done: wait until the whole run has ended.
        await(() -> background.getActiveCount() == 0);
        return portfolio;
    }

    private Company cirrus() {
        return companies.findByCik(CIK).orElseThrow();
    }

    private Long reportId() {
        return reports.findFirstByCompanyIdOrderByFilingDateDesc(cirrus().getId()).orElseThrow().getId();
    }

    private static void await(Supplier<Boolean> done) throws Exception {
        for (int attempt = 0; attempt < 600; attempt++) {
            if (done.get()) return;
            Thread.sleep(50);
        }
        assertThat(done.get()).as("finished in time").isTrue();
    }

    /** The report's text as the SEC's HTML would give it: each paragraph in its own {@code <p>}. */
    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }

    /** Removes what an earlier run saved: Cirrus (and with it its report and figures), and every company named. */
    private void purge() {
        companies.findByCik(CIK).ifPresent(cirrus -> {
            holdings.findAll().stream().filter(holding -> cirrus.getId().equals(holding.getCompanyId()))
                    .forEach(holdings::delete);
            companies.delete(cirrus);
        });
        Set<String> names = new LinkedHashSet<>(List.of("Apple, Inc.", "Foxconn", "Luxshare"));
        for (GeminiAnswer.Found item : answers.read(Examples.geminiNvidia())) {
            names.add(item.company());
            if (!item.commonName().isBlank()) names.add(item.commonName());
            if (!item.alsoKnownAs().isBlank()) names.add(item.alsoKnownAs());
        }
        for (String name : names) {
            nameRows.findByNameKey(CompanyDirectory.nameKey(name)).ifPresent(row ->
                    companies.findById(row.getCompanyId()).ifPresent(company -> {
                        if (company.getCik() == null) {
                            companies.delete(company);
                        } else {
                            company.setReportStatus(null);
                            company.setReportStatusReason(null);
                            companies.save(company);
                        }
                    }));
        }
    }
}
