package com.truesight.report;

import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.company.ReportStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read Each Supplier's Own Report, criteria 1 to 6, through a holding's real Analyse.
 *
 * <p>The holding is AMD: its real 10-K ({@code docs/examples/amd-10k.txt}) and Gemini's real answer for it
 * ({@code gemini-amd.json}), which names seven suppliers with the tickers Gemini gave. The fake SEC knows three of
 * them, GlobalFoundries, UMC and Sanmina, by their real names and SEC numbers, and no other ticker. {@code
 * docs/examples} holds only two real annual reports, so each of the three is served NVIDIA's real 10-K
 * ({@code nvidia-10k.txt}) and Gemini's real answer for it ({@code gemini-nvidia.json}) as its own: NVIDIA's suppliers
 * then stand for the supplier's suppliers. Nothing here calls the SEC or Gemini.
 *
 * <p>The database is shared by every integration test, so each test starts and ends by removing the companies the two
 * answers name, and the three suppliers the fake SEC knows.
 */
@IntegrationTest
class SupplierReaderIT {

    private static final String AMD_CIK = "0000002488";
    private static final String AMD_URL = "https://www.sec.gov/Archives/edgar/data/2488/ts217-amd-10k.htm";
    /** The three suppliers of AMD the fake SEC knows: ticker, the SEC's name and its SEC number. */
    private static final List<String[]> FILERS = List.of(
            new String[] {"GFS", "GLOBALFOUNDRIES Inc.", "0001709048"},
            new String[] {"UMC", "UNITED MICROELECTRONICS CORP", "0001033767"},
            new String[] {"SANM", "SANMINA CORP", "0000897723"});

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory directory;
    @Autowired CompanyRepository companies;
    @Autowired CompanyNameRepository nameRows;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired GeminiAnswer answers;
    @Autowired SupplierReader supplierReader;
    @Autowired AiCallLog aiCallLog;

    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    @BeforeEach
    void fakeTheSecAndGemini() {
        purge();
        when(sec.companyForTicker("AMD")).thenReturn(new SecClient.SecCompany("ADVANCED MICRO DEVICES INC", AMD_CIK, "AMD"));
        when(sec.latestAnnualFiling(AMD_CIK)).thenReturn(
                new SecClient.SecFiling("10-K", "ts217-amd", LocalDate.parse("2026-02-04"), AMD_URL));
        when(sec.download(AMD_URL)).thenReturn(html(Examples.amd10K()));
        for (String[] filer : FILERS) {
            when(sec.companyForTicker(filer[0])).thenReturn(new SecClient.SecCompany(filer[1], filer[2], filer[0]));
            when(sec.latestAnnualFiling(filer[2])).thenReturn(new SecClient.SecFiling("10-K",
                    "ts217-" + filer[0].toLowerCase(Locale.ROOT), LocalDate.parse("2026-02-25"), urlOf(filer[0])));
            when(sec.download(urlOf(filer[0]))).thenReturn(html(Examples.nvidia10K()));
        }
        // AMD's report gets AMD's answer; every other report, a stand-in supplier's, NVIDIA's. Told apart by the
        // prompt's first words, which name the report's company: NVIDIA's 10-K names AMD too, as a competitor.
        when(gemini.generate(any(AiRequest.class))).thenAnswer(call -> {
            AiRequest request = call.getArgument(0);
            boolean amd = request.prompt().toLowerCase(Locale.ROOT).startsWith("below is part of advanced micro devices");
            return answer(amd ? Examples.geminiAmd() : Examples.geminiNvidia());
        });
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
    }

    // ---- criterion 1 ---------------------------------------------------------------------------------------------

    @Test
    void analysingAHoldingAlsoStoresTheReportsAndRelationshipsOfItsThreeSuppliersThatFileWithTheSec() throws Exception {
        Portfolio portfolio = analyse("suppliers-three@example.com");

        for (String[] filer : FILERS) {
            Company supplier = companies.findByCik(filer[2]).orElseThrow();
            // The supplier AMD's report names is the company whose report was read: one row, not two.
            assertThat(amdSupplierIds()).contains(supplier.getId());
            assertThat(supplier.getReportStatus()).isEqualTo(ReportStatus.DONE);
            Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(supplier.getId()).orElseThrow();
            assertThat(report.getUrl()).isEqualTo(urlOf(filer[0]));
            assertThat(relationships.findByReportIdOrderByIdAsc(report.getId())).isNotEmpty();
        }
        verify(gemini, times(4)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.FIND_RELATIONSHIPS));
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
    }

    // ---- criterion 2 ---------------------------------------------------------------------------------------------

    @Test
    void aSuppliersOwnSuppliersAreFoundButTheirReportsAreNotRead() throws Exception {
        analyse("suppliers-of-suppliers@example.com");

        Company sanmina = companies.findByCik("0000897723").orElseThrow();
        Report sanminaReport = reports.findFirstByCompanyIdOrderByFilingDateDesc(sanmina.getId()).orElseThrow();
        List<Long> found = relationships.findByReportIdOrderByIdAsc(sanminaReport.getId()).stream()
                .map(relationship -> relationship.getCounterpartyId()).toList();
        // NVIDIA's memory suppliers, standing for Sanmina's: AMD's report does not name them.
        for (String name : List.of("SK Hynix Inc.", "Micron Technology, Inc.")) {
            Company suppliersSupplier = companyNamed(name);
            assertThat(found).contains(suppliersSupplier.getId());
            assertThat(amdSupplierIds()).doesNotContain(suppliersSupplier.getId());
            assertThat(suppliersSupplier.getReportStatus()).isNull();
            assertThat(reports.findLatestIdByCompanyId(suppliersSupplier.getId())).isEmpty();
        }
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void aHoldingsCustomersReportsAreNotRead() throws Exception {
        analyse("suppliers-not-customers@example.com");

        Company openAi = companyNamed("OpenAI");
        assertThat(openAi.getReportStatus()).isNull();
        assertThat(reports.findLatestIdByCompanyId(openAi.getId())).isEmpty();
    }

    // ---- criterion 4 ---------------------------------------------------------------------------------------------

    @Test
    void aSupplierWhoseReportIsAlreadyStoredIsNotDownloadedOrSentToGeminiAgain() throws Exception {
        // Stored and read whole before: Gemini's answer for it was recorded, with nothing kept.
        Company sanmina = directory.findOrCreate("SANMINA CORP", "0000897723", "SANM", List.of());
        Report stored = reports.save(new Report(sanmina.getId(), "10-K", "ts217-sanm-stored", LocalDate.parse("2026-02-25"),
                urlOf("SANM"), Examples.nvidia10K()));
        AiRequest read = new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "Test prompt.", stored.getId(), "Test prompt.");
        Long callId = aiCallLog.record(read, "test-model", "[]", AiOutcome.ANSWERED, null, java.time.Instant.now(), 1);
        aiCallLog.attachReport(callId, stored.getId());
        aiCallLog.finish(callId, 0, 0);

        analyse("suppliers-stored@example.com");
        analyse("suppliers-stored-again@example.com"); // and every supplier read by the first is stored now

        verify(sec, never()).latestAnnualFiling("0000897723");
        verify(sec, never()).download(urlOf("SANM"));
        verify(sec, times(1)).download(urlOf("GFS"));
        verify(sec, times(1)).download(urlOf("UMC"));
        // AMD's report, then GlobalFoundries' and UMC's: never Sanmina's, and nothing a second time.
        verify(gemini, times(3)).generate(argThat((AiRequest request) -> request.purpose() == AiPurpose.FIND_RELATIONSHIPS));
        assertThat(companies.findById(sanmina.getId()).orElseThrow().getReportStatus()).isNull();
    }

    // ---- criterion 5 ---------------------------------------------------------------------------------------------

    @Test
    void aSupplierTheSecDoesNotKnowIsMarkedNoReportFoundAndIsNotLookedUpAgain() throws Exception {
        analyse("suppliers-unknown@example.com");

        // Samsung's ticker, as Gemini gave it, is not one the SEC knows; Siliconware was given no ticker at all.
        Company samsung = companyNamed("Samsung Electronics");
        assertThat(samsung.getReportStatus()).isEqualTo(ReportStatus.NO_REPORT_FOUND);
        Company siliconware = companyNamed("Siliconware Precision Industries");
        assertThat(siliconware.getReportStatus()).isEqualTo(ReportStatus.NO_REPORT_FOUND);
        String samsungTicker = samsung.getTicker();
        verify(sec, times(1)).companyForTicker(samsungTicker);

        analyse("suppliers-unknown-again@example.com");

        verify(sec, times(1)).companyForTicker(samsungTicker);
        assertThat(companies.findById(samsung.getId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.NO_REPORT_FOUND);
    }

    // ---- criterion 6 ---------------------------------------------------------------------------------------------

    @Test
    void theHoldingIsDoneBeforeItsSuppliersAreReadAndASupplierThatFailsDoesNotFailIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(sec.download(urlOf("GFS"))).thenAnswer(call -> {
            if (!release.await(20, TimeUnit.SECONDS)) throw new AssertionError("Never released.");
            return html(Examples.nvidia10K());
        });
        when(sec.download(urlOf("UMC"))).thenThrow(
                new SecClient.SecAccessException("The SEC returned HTTP 503 for a request.", new RuntimeException()));
        String email = "suppliers-done-first@example.com";
        Portfolio portfolio = portfolioWith(email);
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        Company globalFoundries;
        try {
            // Found by the name AMD's report gives it: its SEC number is saved only once its report is read.
            await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.DONE
                    && nameRows.findByNameKey(CompanyDirectory.nameKey("GlobalFoundries"))
                            .flatMap(row -> companies.findById(row.getCompanyId()))
                            .map(company -> company.getReportStatus() == ReportStatus.WAITING).orElse(false));
            globalFoundries = companyNamed("GlobalFoundries");
            // Done, while GlobalFoundries' report is still downloading.
            assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
            assertThat(holdingOf(portfolio).getStatusReason()).isNull();
            SupplierReader.Progress reading = expectHoldingsToShowProgress(email, portfolio);
            assertThat(reading.read()).isLessThan(reading.toRead());
        } finally {
            release.countDown();
        }
        awaitSuppliersRead(portfolio);

        Company umc = companies.findByCik("0001033767").orElseThrow();
        assertThat(umc.getReportStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(umc.getReportStatusReason()).isEqualTo("The SEC returned HTTP 503 for a request.");
        assertThat(companies.findById(globalFoundries.getId()).orElseThrow().getReportStatus()).isEqualTo(ReportStatus.DONE);
        assertThat(holdingOf(portfolio).getStatus()).isEqualTo(HoldingStatus.DONE);
        assertThat(holdingOf(portfolio).getStatusReason()).isNull();
        SupplierReader.Progress finished = expectHoldingsToShowProgress(email, portfolio);
        assertThat(finished.read()).isEqualTo(finished.toRead()).isPositive();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Checks the holdings say how far reading the suppliers has got, and answers how far. */
    private SupplierReader.Progress expectHoldingsToShowProgress(String email, Portfolio portfolio) throws Exception {
        SupplierReader.Progress progress = supplierReader.progressOf(List.of(holdingOf(portfolio).getCompanyId()));
        mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suppliersRead").value(progress.read()))
                .andExpect(jsonPath("$[0].suppliersToRead").value(progress.toRead()));
        return progress;
    }

    /** A new portfolio holding AMD, analysed, and its suppliers read. */
    private Portfolio analyse(String email) throws Exception {
        Portfolio portfolio = portfolioWith(email);
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        await(() -> holdingOf(portfolio).getStatus() == HoldingStatus.DONE);
        awaitSuppliersRead(portfolio);
        return portfolio;
    }

    private Portfolio portfolioWith(String email) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Suppliers " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "AMD", new BigDecimal("10")));
        return portfolio;
    }

    private Holding holdingOf(Portfolio portfolio) {
        return holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst();
    }

    /** Waits until every supplier asked for has finished, however it went. */
    private void awaitSuppliersRead(Portfolio portfolio) throws Exception {
        await(() -> {
            Long amd = holdingOf(portfolio).getCompanyId();
            SupplierReader.Progress progress = supplierReader.progressOf(List.of(amd));
            return progress.toRead() > 0 && progress.read() == progress.toRead();
        });
    }

    private static void await(Supplier<Boolean> done) throws Exception {
        for (int attempt = 0; attempt < 600; attempt++) {
            if (done.get()) return;
            Thread.sleep(50);
        }
        assertThat(done.get()).as("finished in time").isTrue();
    }

    /** The companies AMD's report names as its suppliers. */
    private Set<Long> amdSupplierIds() {
        return supplierReader.suppliersOf(List.of(companies.findByCik(AMD_CIK).orElseThrow().getId()));
    }

    private Company companyNamed(String name) {
        return nameRows.findByNameKey(CompanyDirectory.nameKey(name))
                .flatMap(row -> companies.findById(row.getCompanyId())).orElseThrow();
    }

    private static String urlOf(String ticker) {
        return "https://www.sec.gov/Archives/edgar/data/ts217/" + ticker.toLowerCase(Locale.ROOT) + "-10k.htm";
    }

    /** The report's text as the SEC's HTML would give it: each paragraph in its own {@code <p>}. */
    private static String html(String text) {
        Document document = Document.createShell("");
        text.lines().forEach(line -> document.body().appendElement("p").text(line));
        return document.outerHtml();
    }

    /**
     * Removes what an earlier test saved: AMD's report, the three suppliers the fake SEC knows, and every company the
     * two answers name. One with an SEC number from elsewhere (another test's holding) is kept, with no report status.
     */
    private void purge() {
        companies.findByCik(AMD_CIK).ifPresent(amd -> reports.deleteByCompanyId(amd.getId()));
        for (String[] filer : FILERS) {
            companies.findByCik(filer[2]).ifPresent(companies::delete);
        }
        Set<String> names = new LinkedHashSet<>();
        for (String answer : List.of(Examples.geminiAmd(), Examples.geminiNvidia())) {
            for (GeminiAnswer.Found item : answers.read(answer)) {
                names.add(item.company());
                if (!item.commonName().isBlank()) names.add(item.commonName());
                if (!item.alsoKnownAs().isBlank()) names.add(item.alsoKnownAs());
            }
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
