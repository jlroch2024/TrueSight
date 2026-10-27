package com.truesight.company;

import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.relationship.GeminiAnswer;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one-off pass that gives the companies found in reports their common names, and joins duplicates, with a fake
 * Gemini that gives its real answer from {@code docs/examples/gemini-common-names.json}: the answer to the pass's
 * prompt, asked once by hand of the names of the companies found in the NVIDIA and AMD reports.
 *
 * <p>Every company the real answer is about is saved first under the name it was stored with before TS-180, together
 * with an SEC holding that is one of those companies under another name ("Sanmina Group Holdings Corp" for the real
 * answer's Sanmina, with its ticker), and a relationship to each. The database is shared by every
 * integration test, so each test starts by removing any earlier company with those names, and removes them again
 * afterwards: what the pass renames here is never left for another test to find.
 */
@IntegrationTest
class CommonNameServiceIT {

    private static final Pattern NAME_LINE = Pattern.compile("(?m)^\\d+\\. ");

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CommonNameService service;
    @Autowired CompanyDirectory directory;
    @Autowired CompanyRepository companies;
    @Autowired CompanyNameRepository nameRows;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired GeminiAnswer answers;
    @MockitoBean GeminiClient gemini;
    @MockitoBean CommonNameJob job;

    private final JsonMapper json = JsonMapper.builder().build();
    private List<GeminiAnswer.CommonNameFound> real;
    /** The company each real stored name was saved as, before the pass. */
    private final Map<String, Long> storedAs = new LinkedHashMap<>();
    /** The relationship from the test's holding to each of those companies. */
    private final Map<String, Long> relationshipTo = new LinkedHashMap<>();
    private final List<Long> extraCompanies = new ArrayList<>();
    /** The real answer's item that the SEC holding below is the same company as: it has a ticker, and a common name. */
    private GeminiAnswer.CommonNameFound listed;
    /** An SEC holding, such as "SONY GROUP CORP" is for a customer "Sony": its name agrees, and it has the ticker. */
    private Company secHolding;
    private Company holding;

    @BeforeEach
    void saveTheStoredCompaniesAndFakeGemini() {
        real = answers.readCommonNames(Examples.geminiCommonNames());
        purge();
        listed = real.stream().filter(item -> item.name().startsWith("Sanmina")).findFirst()
                .or(() -> real.stream().filter(item -> CompanyDirectory.cleanTicker(item.ticker()) != null
                        && !item.commonName().isBlank()).findFirst()).orElseThrow();
        secHolding = directory.findOrCreate(listed.commonName() + " Group Holdings Corp",
                "9" + java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L),
                CompanyDirectory.cleanTicker(listed.ticker()), List.of());
        holding = directory.findOrCreate("Names Test Holding " + UUID.randomUUID(), null, null, List.of());
        extraCompanies.add(holding.getId());
        Report report = reports.save(new Report(holding.getId(), "10-K",
                "nm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), LocalDate.parse("2026-02-04"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        for (GeminiAnswer.CommonNameFound item : real) {
            if (storedAs.containsKey(item.name())) continue;
            Company stored = directory.findOrCreate(item.name(), null, null, List.of());
            storedAs.put(item.name(), stored.getId());
            Relationship relationship = relationships.save(new Relationship(report.getId(), holding.getId(),
                    stored.getId(), RelationshipType.SUPPLIER, "parts", "Evidence for " + item.name() + "."));
            relationshipTo.put(item.name(), relationship.getId());
        }
        when(gemini.generateCommonNames(any(AiRequest.class))).thenReturn(answer(Examples.geminiCommonNames()));
    }

    @AfterEach
    void removeWhatTheTestSaved() {
        purge();
        extraCompanies.forEach(id -> companies.findById(id).ifPresent(companies::delete));
        companies.findById(secHolding.getId()).ifPresent(companies::delete);
    }

    // ---- criterion 5: renames, joins, and a second run changes nothing -----------------------------------------

    @Test
    void renamesEachStoredCompanyToItsCommonNameAndTheOldNameStillFindsIt() {
        service.run();

        for (GeminiAnswer.CommonNameFound item : real) {
            Company owner = whoNowOwns(item.name());
            String common = item.commonName().isBlank() ? item.name() : item.commonName();
            if (companies.findById(storedAs.get(item.name())).isPresent()) {
                // Renamed where it stood: the common name is its name, and the old name is another name of it.
                assertThat(owner.getId()).isEqualTo(storedAs.get(item.name()));
                assertThat(CompanyDirectory.nameKey(owner.getName())).isEqualTo(CompanyDirectory.nameKey(common));
            } else {
                // Joined into another company, which the old name still finds.
                assertThat(owner.getId()).isNotEqualTo(storedAs.get(item.name()));
            }
        }
    }

    @Test
    void openAiOpCoIsRenamedOpenAiAndTheWrittenNameStaysAsAnotherName() {
        GeminiAnswer.CommonNameFound openAi = real.stream()
                .filter(item -> item.name().startsWith("OpenAI OpCo")).findFirst().orElseThrow();
        assertThat(openAi.commonName()).isEqualTo("OpenAI");

        service.run();

        Company company = whoNowOwns(openAi.name());
        assertThat(company.getName()).isEqualTo("OpenAI");
        assertThat(directory.findOrCreate(openAi.name(), null, null, List.of()).getId()).isEqualTo(company.getId());
        assertThat(directory.findOrCreate("OpenAI", null, null, List.of()).getId()).isEqualTo(company.getId());
    }

    @Test
    void aTickerGeminiGivesIsSavedOnTheCompany() {
        service.run();

        for (GeminiAnswer.CommonNameFound item : real) {
            String ticker = CompanyDirectory.cleanTicker(item.ticker());
            if (ticker != null) {
                assertThat(whoNowOwns(item.name()).getTicker()).isNotNull();
            }
        }
    }

    @Test
    void aStoredCompanyWhoseCommonNameAndTickerMatchAnSecCompanyIsJoinedToItAndItsRelationshipsMove() {
        // As "Sony" is joined to "SONY GROUP CORP": the same ticker, and names that agree.
        assertThat(CompanyDirectory.nameKey(listed.name())).isNotEqualTo(CompanyDirectory.nameKey(secHolding.getName()));

        service.run();

        assertThat(companies.findById(storedAs.get(listed.name()))).isEmpty(); // the duplicate is gone
        assertThat(whoNowOwns(listed.name()).getId()).isEqualTo(secHolding.getId());
        assertThat(relationships.findById(relationshipTo.get(listed.name())).orElseThrow().getCounterpartyId())
                .isEqualTo(secHolding.getId());
        Company group = companies.findById(secHolding.getId()).orElseThrow();
        assertThat(group.getName()).isEqualTo(listed.commonName() + " Group Holdings Corp"); // the SEC's own name stays
        assertThat(group.getCik()).isNotNull();
    }

    @Test
    void everyRelationshipEndsUpPointingAtTheCompanyItsStoredNameNowBelongsTo() {
        service.run();

        for (GeminiAnswer.CommonNameFound item : real) {
            Relationship relationship = relationships.findById(relationshipTo.get(item.name())).orElse(null);
            if (relationship == null) {
                continue; // dropped because it duplicated another after a join
            }
            assertThat(relationship.getCounterpartyId()).isEqualTo(whoNowOwns(item.name()).getId());
            assertThat(relationship.getCompanyId()).isEqualTo(holding.getId());
        }
    }

    @Test
    void runningItASecondTimeChangesNothing() {
        service.run();
        List<String> afterFirst = snapshot();

        service.run();

        assertThat(snapshot()).isEqualTo(afterFirst);
    }

    @Test
    void theEvidenceOfEveryRelationshipIsUntouched() {
        service.run();

        for (GeminiAnswer.CommonNameFound item : real) {
            relationships.findById(relationshipTo.get(item.name())).ifPresent(relationship ->
                    assertThat(relationship.getEvidence()).isEqualTo("Evidence for " + item.name() + "."));
        }
    }

    // ---- what is sent -------------------------------------------------------------------------------------------

    @Test
    void theNamesGoToGeminiInBatchesOfAtMostFiftyForThePurposeCommonNames() {
        for (int i = 1; i <= 120; i++) {
            extraCompanies.add(directory.findOrCreate("Batch Company " + i + " " + UUID.randomUUID(), null, null,
                    List.of()).getId());
        }
        int withoutSecNumber = companies.findByCikIsNullOrderByIdAsc().size();

        service.run();

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, atLeastOnce()).generateCommonNames(sent.capture());
        List<Integer> sizes = sent.getAllValues().stream().map(request -> namesIn(request.prompt())).toList();
        assertThat(sizes).allSatisfy(size -> assertThat(size).isBetween(1, 50));
        assertThat(sizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(withoutSecNumber);
        assertThat(sizes).hasSize((withoutSecNumber + 49) / 50);
        assertThat(sent.getAllValues()).allSatisfy(request -> assertThat(request.purpose()).isEqualTo(AiPurpose.COMMON_NAMES));
    }

    @Test
    void onlyCompaniesWithoutASecNumberAreSent() {
        service.run();

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, atLeastOnce()).generateCommonNames(sent.capture());
        String allSent = String.join("\n", sent.getAllValues().stream().map(AiRequest::prompt).toList());
        assertThat(allSent).doesNotContain(secHolding.getName());
        assertThat(allSent).contains(real.getFirst().name());
    }

    @Test
    void thePromptTellsGeminiToGiveTheCompanysOwnCommonNameNeverItsParentsOrASisterCompanys() {
        service.run();

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, atLeastOnce()).generateCommonNames(sent.capture());
        assertThat(sent.getAllValues().getFirst().prompt())
                .contains("commonName:")
                .contains("ticker:")
                .contains("this company's own name, never its parent's, a sister company's or another company's in its group")
                .contains("from your own knowledge")
                .contains("JSON only");
        assertThat(sent.getAllValues().getFirst().recordedPrompt()).isEqualTo(sent.getAllValues().getFirst().prompt());
    }

    // ---- when Gemini does not answer everything ---------------------------------------------------------------

    @Test
    void aNameGeminiLeavesOutKeepsItsName() throws Exception {
        GeminiAnswer.CommonNameFound left = real.getFirst();
        List<Map<String, Object>> others = real.stream().filter(item -> item != left)
                .map(item -> Map.<String, Object>of("name", item.name(), "commonName", item.commonName(),
                        "ticker", item.ticker())).toList();
        when(gemini.generateCommonNames(any(AiRequest.class))).thenReturn(answer(response(others)));

        service.run();

        assertThat(companies.findById(storedAs.get(left.name())).orElseThrow().getName()).isEqualTo(left.name());
    }

    @Test
    void aFailedCallChangesNothingAndThePassCanBeRunAgain() {
        when(gemini.generateCommonNames(any(AiRequest.class)))
                .thenThrow(new GeminiClient.GeminiException("The AI could not be reached. Try again later.", null));
        List<String> before = snapshot();

        service.run();

        assertThat(snapshot()).isEqualTo(before);

        when(gemini.generateCommonNames(any(AiRequest.class))).thenReturn(answer(Examples.geminiCommonNames()));
        service.run();
        String openAiAsWritten = real.stream().map(GeminiAnswer.CommonNameFound::name)
                .filter(name -> name.startsWith("OpenAI OpCo")).findFirst().orElseThrow();
        assertThat(whoNowOwns(openAiAsWritten).getName()).isEqualTo("OpenAI");
    }

    @Test
    void anUnreadableAnswerIsAskedAgainOnceAndChangesNothingIfItStaysUnreadable() {
        when(gemini.generateCommonNames(any(AiRequest.class))).thenReturn(answer("<html>Bad gateway</html>"));
        List<String> before = snapshot();
        int batches = (companies.findByCikIsNullOrderByIdAsc().size() + 49) / 50;

        service.run();

        assertThat(snapshot()).isEqualTo(before);
        verify(gemini, times(batches * 2)).generateCommonNames(any(AiRequest.class));
    }

    // ---- criterion 6: only an analyst can start it -------------------------------------------------------------

    @Test
    void anAnalystStartsThePassAndIsAnsweredAccepted() throws Exception {
        mockMvc.perform(post("/api/companies/common-names")
                        .header("Authorization", logins.analystBearer("common-names-analyst@example.com")))
                .andExpect(status().isAccepted());

        verify(job).run();
    }

    @Test
    void portfolioManagersGetForbiddenAndNothingRuns() throws Exception {
        mockMvc.perform(post("/api/companies/common-names")
                        .header("Authorization", logins.bearer("common-names-manager@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));

        verify(job, never()).run();
        verify(gemini, never()).generateCommonNames(any(AiRequest.class));
    }

    @Test
    void theStartNeedsALogin() throws Exception {
        mockMvc.perform(post("/api/companies/common-names")).andExpect(status().isUnauthorized());

        verify(job, never()).run();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** The company that now owns this stored name: the renamed company, or the one it was joined into. */
    private Company whoNowOwns(String storedName) {
        CompanyName row = nameRows.findByNameKey(CompanyDirectory.nameKey(storedName)).orElseThrow();
        return companies.findById(row.getCompanyId()).orElseThrow();
    }

    private static int namesIn(String prompt) {
        return (int) NAME_LINE.matcher(prompt).results().count();
    }

    /** Gemini's response around the text of an answer. */
    private String response(List<Map<String, Object>> items) throws Exception {
        String content = json.writeValueAsString(items);
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", content)))))));
    }

    /** Everything the pass could change, in a form two moments can be compared by. */
    private List<String> snapshot() {
        List<String> rows = new ArrayList<>();
        companies.findAll().stream().sorted(Comparator.comparing(Company::getId))
                .forEach(c -> rows.add("company " + c.getId() + "|" + c.getName() + "|" + c.getTicker() + "|" + c.getCik()));
        nameRows.findAll().stream().sorted(Comparator.comparing(CompanyName::getId))
                .forEach(n -> rows.add("name " + n.getId() + "|" + n.getCompanyId() + "|" + n.getNameKey() + "|" + n.getName()));
        relationships.findAll().stream().sorted(Comparator.comparing(Relationship::getId))
                .forEach(r -> rows.add("relationship " + r.getId() + "|" + r.getCompanyId() + "|" + r.getCounterpartyId()));
        return rows;
    }

    /**
     * Removes the companies the real answer is about, however an earlier test or this one saved them, so one test's
     * renames and joins never reach another. A company with an SEC number is kept (a holding may hold it): only the
     * names this test gave it are removed.
     */
    private void purge() {
        Set<String> names = new LinkedHashSet<>();
        for (GeminiAnswer.CommonNameFound item : answers.readCommonNames(Examples.geminiCommonNames())) {
            names.add(item.name());
            if (!item.commonName().isBlank()) names.add(item.commonName());
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
        storedAs.clear();
        relationshipTo.clear();
    }
}
