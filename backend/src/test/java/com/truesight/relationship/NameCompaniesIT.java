package com.truesight.relationship;

import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyName;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Name Companies the Way People Know Them, through analysing a report: AMD's real 10-K from
 * {@code docs/examples/amd-10k.txt}, and Gemini's real answer to the relationship prompt that also asks for each
 * company's common name and ticker, from {@code docs/examples/gemini-amd.json}. Nothing here calls the SEC or Gemini.
 *
 * <p>The database is shared by every integration test, so each test starts by removing any earlier company with the
 * names the real answer gives, and removes them again afterwards.
 */
@IntegrationTest
class NameCompaniesIT {

    private static final String AMD_CIK = "0000002488";

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
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    private List<GeminiAnswer.Found> real;
    /** An SEC holding that one of AMD's companies is the same as, under another name: the way "SONY GROUP CORP" is "Sony". */
    private Company secHolding;
    private GeminiAnswer.Found joined;
    private Report amdReport;
    private final List<Long> extraCompanies = new java.util.ArrayList<>();

    @BeforeEach
    void saveAmdsReportAndFakeGemini() {
        real = answers.read(Examples.geminiAmd());
        purge();
        // AMD's latest 10-K names no Sony, so the story's Sony case is made with a company the real answer does give,
        // a listed one: the holding has that company's ticker and a name that agrees with its common name.
        joined = real.stream().filter(item -> item.company().startsWith("Sanmina") && quoted(item)).findFirst()
                .or(() -> real.stream().filter(item -> CompanyDirectory.cleanTicker(item.ticker()) != null
                        && !item.commonName().isBlank() && quoted(item)).findFirst()).orElseThrow();
        secHolding = directory.findOrCreate(joined.commonName() + " Group Holdings Corp",
                "9" + java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L),
                CompanyDirectory.cleanTicker(joined.ticker()), List.of());
        Company amd = directory.findOrCreate("ADVANCED MICRO DEVICES INC", AMD_CIK, "AMD", List.of());
        reports.deleteByCompanyId(amd.getId()); // and, with it, any relationships found in it before
        amdReport = reports.save(new Report(amd.getId(), "10-K", "amd-names-it-10k", LocalDate.parse("2026-02-04"),
                "https://www.sec.gov/Archives/edgar/data/2488/", Examples.amd10K()));
        when(sec.companyForTicker("AMD")).thenReturn(new SecClient.SecCompany("ADVANCED MICRO DEVICES INC", AMD_CIK, "AMD"));
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiAmd()));
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
        extraCompanies.forEach(id -> companies.findById(id).ifPresent(companies::delete));
        companies.findById(secHolding.getId()).ifPresent(companies::delete);
    }

    // ---- criterion 1 ---------------------------------------------------------------------------------------------

    @Test
    void aCustomerAReportCallsOpenAiOpCoIsSavedAsOpenAiWithTheWrittenNameKeptAsAnotherName() throws Exception {
        GeminiAnswer.Found openAi = real.stream()
                .filter(item -> item.company().startsWith("OpenAI OpCo") && quoted(item)).findFirst().orElseThrow();
        analyse("names-openai@example.com");

        Relationship relationship = relationshipFor(openAi);
        Company counterparty = companies.findById(relationship.getCounterpartyId()).orElseThrow();
        assertThat(counterparty.getName()).isEqualTo("OpenAI");
        // The name as the report writes it, and any short name it gives, still find the same company.
        assertThat(directory.findOrCreate(openAi.company(), null, null, List.of()).getId()).isEqualTo(counterparty.getId());
        if (!openAi.alsoKnownAs().isBlank()) {
            assertThat(directory.findOrCreate(openAi.alsoKnownAs(), null, null, List.of()).getId())
                    .isEqualTo(counterparty.getId());
        }
        assertThat(directory.findOrCreate("OpenAI", null, null, List.of()).getId()).isEqualTo(counterparty.getId());
    }

    @Test
    void theEvidenceIsStillTheReportsOwnSentenceWithTheNameAsItIsWritten() throws Exception {
        GeminiAnswer.Found openAi = real.stream()
                .filter(item -> item.company().startsWith("OpenAI OpCo") && quoted(item)).findFirst().orElseThrow();
        analyse("names-evidence@example.com");

        Relationship relationship = relationshipFor(openAi);
        assertThat(relationship.getEvidence())
                .isEqualTo(new QuoteChecker(Examples.amd10K()).evidenceFor(openAi.quote()).orElseThrow());
        assertThat(Examples.amd10K()).contains(relationship.getEvidence().replaceAll("\\s+", " ").trim().substring(0, 40));
    }

    @Test
    void everyCompanyIsSavedUnderItsCommonNameWhenGeminiGaveOneAndItsTickerWhenItIsListed() throws Exception {
        analyse("names-every@example.com");

        for (GeminiAnswer.Found item : real) {
            Relationship relationship = relationshipOrNull(item);
            if (relationship == null) continue; // its quote is not in the report, so it was dropped
            Company counterparty = companies.findById(relationship.getCounterpartyId()).orElseThrow();
            String expected = item.commonName().isBlank() ? item.company() : item.commonName();
            if (counterparty.getCik() == null) {
                assertThat(counterparty.getName()).isEqualToIgnoringCase(expected);
            }
            String ticker = CompanyDirectory.cleanTicker(item.ticker());
            if (ticker != null && counterparty.getTicker() != null) {
                assertThat(counterparty.getTicker()).isEqualToIgnoringCase(ticker);
            }
        }
    }

    // ---- criterion 2 ---------------------------------------------------------------------------------------------

    @Test
    void aFoundCompanyWithAnExistingCompanysTickerAndAnAgreeingNameIsThatCompanySoTheGraphShowsItOnce() throws Exception {
        analyse("names-joined@example.com");

        Relationship relationship = relationshipFor(joined);
        assertThat(relationship.getCounterpartyId()).isEqualTo(secHolding.getId());
        assertThat(companies.findById(secHolding.getId()).orElseThrow().getName())
                .isEqualTo(joined.commonName() + " Group Holdings Corp"); // the SEC's own name stays
        // Every counterparty of AMD's report with this company's name is that one company.
        String first = joined.commonName().toLowerCase(Locale.ROOT).split(" ")[0];
        Set<Long> same = relationships.findByReportIdOrderByIdAsc(amdReport.getId()).stream()
                .map(Relationship::getCounterpartyId)
                .filter(id -> companies.findById(id).orElseThrow().getName().toLowerCase(Locale.ROOT).startsWith(first))
                .collect(Collectors.toSet());
        assertThat(same).containsExactly(secHolding.getId());
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void aFoundCompanyWhoseTickerMatchesAnExistingCompanyWithAnUnrelatedNameStaysSeparate() throws Exception {
        GeminiAnswer.Found listed = real.stream()
                .filter(item -> CompanyDirectory.cleanTicker(item.ticker()) != null && quoted(item))
                .findFirst().orElseThrow();
        String ticker = CompanyDirectory.cleanTicker(listed.ticker());
        Company unrelated = directory.findOrCreate("Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                + " Foods", null, ticker, List.of());
        extraCompanies.add(unrelated.getId());
        analyse("names-unrelated@example.com");

        Relationship relationship = relationshipFor(listed);
        assertThat(relationship.getCounterpartyId()).isNotEqualTo(unrelated.getId());
        assertThat(companies.findById(unrelated.getId())).isPresent();
        assertThat(companies.findById(unrelated.getId()).orElseThrow().getTicker()).isEqualTo(ticker);
    }

    // ---- criterion 4 ---------------------------------------------------------------------------------------------

    @Test
    void thePromptTellsGeminiToGiveTheCompanysOwnCommonNameNeverItsParentsOrASisterCompanys() throws Exception {
        analyse("names-prompt@example.com");

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini).generate(sent.capture());
        assertThat(sent.getValue().prompt())
                .contains("- company: the company's name, as written in the text")
                .contains("- alsoKnownAs: a short name the text gives for it")
                .contains("- commonName: the name this company is commonly known by, from your own knowledge; "
                        + "this company's own name, never its parent's, a sister company's or another company's in "
                        + "its group; the name as written if you are not sure")
                .contains("- ticker: its main stock ticker if it is listed, from your own knowledge, or an empty string");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** The relationship saved for this answer item: the one whose evidence is the report's own sentence for its quote. */
    private Relationship relationshipFor(GeminiAnswer.Found item) {
        Relationship relationship = relationshipOrNull(item);
        assertThat(relationship).as("a relationship for the quote of %s", item.company()).isNotNull();
        return relationship;
    }

    /**
     * The relationship saved for this item, or null if its quote is not in the report, so it was dropped. Several
     * companies can share one sentence, so the counterparty must be the company this item's written name belongs to.
     */
    private Relationship relationshipOrNull(GeminiAnswer.Found item) {
        String evidence = new QuoteChecker(Examples.amd10K()).evidenceFor(item.quote()).orElse(null);
        Long counterpartyId = nameRows.findByNameKey(CompanyDirectory.nameKey(item.company()))
                .map(CompanyName::getCompanyId).orElse(null);
        if (evidence == null || counterpartyId == null) return null;
        return relationships.findByReportIdOrderByIdAsc(amdReport.getId()).stream()
                .filter(saved -> saved.getEvidence().equals(evidence) && saved.getType() == item.relationship()
                        && saved.getCounterpartyId().equals(counterpartyId))
                .findFirst().orElse(null);
    }

    /** Whether the item's quote is word for word in AMD's report, so the item is kept, not dropped. */
    private static boolean quoted(GeminiAnswer.Found item) {
        return new QuoteChecker(Examples.amd10K()).evidenceFor(item.quote()).isPresent();
    }

    /**
     * Removes the companies the real answer names, however an earlier test or this one saved them, so one test never
     * meets another's. A company with an SEC number is kept: only the names this test gave it are removed.
     */
    private void purge() {
        Set<String> names = new LinkedHashSet<>();
        for (GeminiAnswer.Found item : answers.read(Examples.geminiAmd())) {
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
                            nameRows.delete(row);
                        }
                    }));
        }
    }

    /** Makes a portfolio holding AMD, presses Analyse, and waits for the holding to be done. */
    private Portfolio analyse(String email) throws Exception {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Names Test " + System.nanoTime()));
        holdings.save(new Holding(portfolio.getId(), "AMD", new BigDecimal("100")));
        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 400; attempt++) {
            if (holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus() == HoldingStatus.DONE) {
                return portfolio;
            }
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolio.getId()).getFirst().getStatus())
                .isEqualTo(HoldingStatus.DONE);
        return portfolio;
    }
}
