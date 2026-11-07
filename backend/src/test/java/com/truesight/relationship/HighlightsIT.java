package com.truesight.relationship;

import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.truesight.support.FakeAi.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Highlight What Matters in a Quote through the whole app and a real database. Gemini answers with its real, saved answer
 * for the quotes in {@code docs/examples/}.
 */
@IntegrationTest
class HighlightsIT {

    private static final List<String> QUOTES = Examples.highlightQuotes();

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired RelationshipHighlightRepository highlights;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean GeminiClient gemini;
    @MockitoBean SecClient sec;

    /** The test database is shared, so every other test's relationships are marked as read: only this test's are sent. */
    @BeforeEach
    void everyQuoteSoFarHasBeenRead() {
        jdbc.update("update relationships set highlighted_at = now() where highlighted_at is null");
        when(gemini.generateHighlights(any(AiRequest.class))).thenReturn(answer(Examples.geminiHighlights()));
    }

    @Test
    void afterAnalysisSavesAHoldingsRelationshipsEachQuoteIsReadAndTheApiSendsTheHighlights() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String ticker = "T" + suffix.substring(0, 5).toUpperCase();
        String url = "https://www.sec.gov/Archives/highlights-test/" + suffix;
        when(sec.companyForTicker(ticker)).thenReturn(new SecClient.SecCompany("Highlights Test " + suffix, suffix, ticker));
        when(sec.latestAnnualFiling(suffix)).thenReturn(new SecClient.SecFiling(
                "10-K", "ht-" + suffix, LocalDate.parse("2026-02-25"), url));
        when(sec.download(url)).thenReturn("<html><body><h1>Business</h1><p>" + QUOTES.get(0) + "</p></body></html>");
        when(gemini.generate(any(AiRequest.class))).thenReturn(answer(Examples.geminiNvidia()));
        String email = "highlights-analysis-" + suffix + "@example.com";
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Highlights " + suffix));
        Holding holding = holdings.save(new Holding(portfolio.getId(), ticker, new BigDecimal("10")));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isAccepted());

        Long companyId = awaitCompanyOf(holding.getId());
        List<Relationship> found = awaitRead(companyId);
        assertThat(found).isNotEmpty();
        // The first relationship found states the first saved quote, so the answer's first item is about it.
        Relationship first = found.getFirst();
        assertThat(first.getEvidence()).isEqualTo(QUOTES.get(0));
        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId()).header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].highlights[*].kind").value(
                        contains("COMPANY", "COMPANY", "COMPANY", "COMPANY", "SUPPLIED")))
                .andExpect(jsonPath("$[0].highlights[0].start").value(QUOTES.get(0).indexOf("Taiwan")))
                .andExpect(jsonPath("$[0].highlights[0].end").value(
                        QUOTES.get(0).indexOf(", or TSMC")));
    }

    @Test
    void relationshipsAnalysedBeforeThisStoryAreReadWhenAnAnalystStartsThePass() throws Exception {
        Fixture fixture = fixture();
        List<Relationship> old = new ArrayList<>();
        for (int i = 0; i < QUOTES.size(); i++) {
            old.add(relationship(fixture, QUOTES.get(i)));
        }
        assertThat(old).allMatch(relationship -> relationship.getHighlightedAt() == null);

        mockMvc.perform(post("/api/relationships/highlights")
                        .header("Authorization", logins.analystBearer("highlights-pass-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isAccepted());

        List<Relationship> read = awaitRead(fixture.company().getId());
        assertThat(read).hasSize(QUOTES.size()).allMatch(relationship -> relationship.getHighlightedAt() != null);
        assertThat(textsOf(old.get(3))).containsExactly("October 2025", "OpenAI OpCo, LLC", "OpenAI", "6 gigawatts",
                "AMD GPUs", "AMD Instinct MI450 series products");

        // A second pass has nothing to read, so Gemini is not asked again.
        mockMvc.perform(post("/api/relationships/highlights")
                        .header("Authorization", logins.analystBearer("highlights-again-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isAccepted());
        Thread.sleep(200);
        verify(gemini, times(1)).generateHighlights(any(AiRequest.class));
        assertThat(highlights.findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(List.of(old.get(3).getId()))).hasSize(6);
    }

    @Test
    void portfolioManagersGet403AndThePassDoesNotStart() throws Exception {
        relationship(fixture(), QUOTES.get(0));

        mockMvc.perform(post("/api/relationships/highlights")
                        .header("Authorization", logins.bearer("highlights-pm-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isForbidden());

        Thread.sleep(200);
        verify(gemini, never()).generateHighlights(any(AiRequest.class));
    }

    @Test
    void aRelationshipAnAnalystAddsIsReadForHighlightsLikeAnyOther() throws Exception {
        Fixture fixture = fixture();
        String analyst = logins.analystBearer("highlights-add-" + UUID.randomUUID() + "@example.com");
        String body = JsonBody.add(fixture.company().getId(), "TSMC Highlights " + UUID.randomUUID(), QUOTES.get(0));

        mockMvc.perform(post("/api/relationships").header("Authorization", analyst)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        List<Relationship> read = awaitRead(fixture.company().getId());
        assertThat(read).hasSize(1);
        assertThat(read.getFirst().getSource()).isEqualTo(RelationshipSource.ANALYST);
        assertThat(textsOf(read.getFirst())).containsExactly("Taiwan Semiconductor Manufacturing Company Limited", "TSMC",
                "Samsung Electronics Co., Ltd.", "Samsung", "semiconductor wafers");
    }

    @Test
    void anUnreadableAnswerLeavesTheQuotePlainAndTheNextPassReadsItAgain() throws Exception {
        Fixture fixture = fixture();
        Relationship relationship = relationship(fixture, QUOTES.get(0));
        String analyst = logins.analystBearer("highlights-unreadable-" + UUID.randomUUID() + "@example.com");
        when(gemini.generateHighlights(any(AiRequest.class))).thenReturn(answer("This is not the JSON asked for."));

        mockMvc.perform(post("/api/relationships/highlights").header("Authorization", analyst))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 200 && org.mockito.Mockito.mockingDetails(gemini).getInvocations().isEmpty(); attempt++) {
            Thread.sleep(25);
        }
        Thread.sleep(200);

        Relationship plain = relationships.findById(relationship.getId()).orElseThrow();
        assertThat(plain.getHighlightedAt()).isNull();
        assertThat(textsOf(relationship)).isEmpty();
        mockMvc.perform(get("/api/reviews").header("Authorization", analyst))
                .andExpect(jsonPath("$[?(@.relationship.id == %d)].highlights[*]".formatted(relationship.getId()), empty()));

        when(gemini.generateHighlights(any(AiRequest.class))).thenReturn(answer(Examples.geminiHighlights()));
        mockMvc.perform(post("/api/relationships/highlights").header("Authorization", analyst))
                .andExpect(status().isAccepted());

        assertThat(awaitRead(fixture.company().getId())).hasSize(1);
        assertThat(textsOf(relationship)).hasSize(5);
    }

    @Test
    void theReviewQueueSendsTheSameHighlightsOrderedByWhereTheyStart() throws Exception {
        Fixture fixture = fixture();
        Relationship relationship = relationship(fixture, QUOTES.get(0));
        String analyst = logins.analystBearer("highlights-review-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(post("/api/relationships/highlights").header("Authorization", analyst))
                .andExpect(status().isAccepted());
        awaitRead(fixture.company().getId());
        List<Integer> starts = highlights.findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(List.of(relationship.getId()))
                .stream().map(RelationshipHighlight::getStartAt).toList();
        assertThat(starts).hasSize(5).isSorted();

        String item = "$[?(@.relationship.id == %d)]".formatted(relationship.getId());
        mockMvc.perform(get("/api/reviews").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".highlights[*].start").value(contains(starts.toArray())))
                .andExpect(jsonPath(item + ".relationship.highlights[*].start").value(contains(starts.toArray())));
    }

    /** Waits for every relationship of this company to be read, and returns them, oldest first. */
    private List<Relationship> awaitRead(Long companyId) throws InterruptedException {
        List<Relationship> found = List.of();
        for (int attempt = 0; attempt < 400; attempt++) {
            found = relationships.findByCompanyIdIn(List.of(companyId)).stream()
                    .sorted(java.util.Comparator.comparing(Relationship::getId)).toList();
            if (!found.isEmpty() && found.stream().allMatch(relationship -> relationship.getHighlightedAt() != null)) {
                return found;
            }
            Thread.sleep(25);
        }
        assertThat(found).isNotEmpty().allMatch(relationship -> relationship.getHighlightedAt() != null);
        return found;
    }

    private Long awaitCompanyOf(Long holdingId) throws InterruptedException {
        for (int attempt = 0; attempt < 400; attempt++) {
            Holding holding = holdings.findById(holdingId).orElseThrow();
            if (holding.getStatus() == HoldingStatus.DONE) {
                return holding.getCompanyId();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("The holding was never analysed.");
    }

    /** The words each saved highlight covers, in the order they appear. */
    private List<String> textsOf(Relationship relationship) {
        return highlights.findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(List.of(relationship.getId())).stream()
                .map(h -> relationship.getEvidence().substring(h.getStartAt(), h.getEndAt()))
                .toList();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Company company = companyDirectory.findOrCreate("Highlights Test " + suffix, null, null, List.of());
        Report report = reports.save(new Report(company.getId(), "10-K",
                "hl-" + suffix.replace("-", "").substring(0, 20), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/highlights-test/" + suffix, String.join("\n", QUOTES)));
        return new Fixture(company, report);
    }

    private Relationship relationship(Fixture fixture, String evidence) {
        Company counterparty = companyDirectory.findOrCreate("Highlights Counterparty " + UUID.randomUUID(), null, null, List.of());
        return relationships.save(new Relationship(fixture.report().getId(), fixture.company().getId(), counterparty.getId(),
                RelationshipType.SUPPLIER, null, evidence));
    }

    private record Fixture(Company company, Report report) {
    }

    /** The body of an Add Relationship request. */
    private static final class JsonBody {
        static String add(Long companyId, String counterparty, String sentence) {
            return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of(
                    "companyId", companyId, "counterpartyName", counterparty, "type", "SUPPLIER", "sentence", sentence));
        }
    }
}
