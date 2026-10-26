package com.truesight.portfolio;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.news.Alert;
import com.truesight.news.AlertHolding;
import com.truesight.news.AlertHoldingRepository;
import com.truesight.news.AlertRepository;
import com.truesight.news.NewsArticle;
import com.truesight.news.NewsArticleRepository;
import com.truesight.relationship.GeminiClient;
import com.truesight.report.SecClient;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edit or Remove Holdings, through the whole app and a real database: change a weight, remove a holding (and its
 * place in alerts), and add one whose analysis starts at once. Each test logs in as a brand-new user with one empty
 * portfolio.
 */
@IntegrationTest
class HoldingEditIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired NewsArticleRepository articles;
    @Autowired AlertRepository alerts;
    @Autowired AlertHoldingRepository alertHoldings;

    // The SEC knows none of the tickers used here, so an added holding's analysis ends at No Report Found, and the
    // tests never reach Gemini.
    @MockitoBean SecClient sec;
    @MockitoBean GeminiClient gemini;

    String me;
    long userId;
    long portfolioId;

    @BeforeEach
    void logInWithAnEmptyPortfolio() {
        String email = "pm-" + UUID.randomUUID() + "@example.com";
        me = logins.bearer(email);
        userId = logins.user(email).getId();
        portfolioId = portfolios.save(new Portfolio(userId, "Edit Test")).getId();
    }

    // ---- Acceptance Criterion 1: editing a weight --------------------------------------------------------------

    @Test
    void editingAWeightSavesItAndTheListShowsIt() throws Exception {
        Holding holding = held("NVDA", "10");

        changeWeight(me, portfolioId, holding.getId(), "{\"weight\": 25.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("NVDA"))
                .andExpect(jsonPath("$.weight").value(25.5));

        list(portfolioId).andExpect(jsonPath("$[0].weight").value(25.5));
    }

    @Test
    void aBlankWeightMeansNoWeight() throws Exception {
        Holding holding = held("NVDA", "10");

        changeWeight(me, portfolioId, holding.getId(), "{\"weight\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(nullValue()));
        list(portfolioId).andExpect(jsonPath("$[0].weight").value(nullValue()));

        changeWeight(me, portfolioId, holding.getId(), "{\"weight\": 40}").andExpect(status().isOk());
        changeWeight(me, portfolioId, holding.getId(), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(nullValue()));
    }

    @Test
    void theEdgesZeroAndOneHundredAreAllowed() throws Exception {
        Holding holding = held("NVDA", "10");

        changeWeight(me, portfolioId, holding.getId(), "{\"weight\": 0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(0));
        changeWeight(me, portfolioId, holding.getId(), "{\"weight\": 100}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(100));
    }

    @Test
    void aWeightOutsideZeroToOneHundredIsRefusedWith400AndTheOldWeightStays() throws Exception {
        Holding holding = held("NVDA", "10");

        for (String weight : List.of("-0.1", "-5", "100.1", "101", "1000")) {
            changeWeight(me, portfolioId, holding.getId(), "{\"weight\": " + weight + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("The weight must be between 0 and 100."));
        }

        list(portfolioId).andExpect(jsonPath("$[0].weight").value(10.0));
    }

    // ---- Acceptance Criterion 2: removing a holding, and its place in alerts -----------------------------------

    @Test
    void removingAHoldingRemovesItAndItsPlaceInAlertsAndDeletesAlertsLeftWithNoHoldings() throws Exception {
        Company company = companyDirectory.findOrCreate("Removal Test " + UUID.randomUUID(), null, null, List.of());
        Holding removed = held("AAA", "10");
        Holding kept = held("BBB", "20");
        // One alert reaches both holdings; the other reaches only the one that is removed.
        Alert reachesBoth = alert(company, "30");
        Alert reachesOnlyTheRemoved = alert(company, "10");
        alertHoldings.save(new AlertHolding(reachesBoth.getId(), removed.getId(), null));
        alertHoldings.save(new AlertHolding(reachesBoth.getId(), kept.getId(), null));
        alertHoldings.save(new AlertHolding(reachesOnlyTheRemoved.getId(), removed.getId(), null));

        mockMvc.perform(delete("/api/portfolios/{id}/holdings/{holdingId}", portfolioId, removed.getId())
                        .header("Authorization", me))
                .andExpect(status().isNoContent());

        list(portfolioId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticker").value("BBB"));
        assertThat(alerts.findByPortfolioId(portfolioId)).extracting(Alert::getId).containsExactly(reachesBoth.getId());
        assertThat(alertHoldings.findByAlertId(reachesBoth.getId()))
                .extracting(AlertHolding::getHoldingId).containsExactly(kept.getId());
        assertThat(alertHoldings.findByAlertId(reachesOnlyTheRemoved.getId())).isEmpty();
    }

    @Test
    void removingAHoldingNoAlertReachesJustRemovesIt() throws Exception {
        Holding holding = held("AAA", "10");

        mockMvc.perform(delete("/api/portfolios/{id}/holdings/{holdingId}", portfolioId, holding.getId())
                        .header("Authorization", me))
                .andExpect(status().isNoContent());

        list(portfolioId).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Acceptance Criterion 3: adding a holding --------------------------------------------------------------

    @Test
    void addingATickerSavesItWaitingAndItsAnalysisStartsAtOnce() throws Exception {
        add(me, portfolioId, "{\"ticker\": \"newco\", \"weight\": 12.5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("NEWCO"))
                .andExpect(jsonPath("$.weight").value(12.5))
                .andExpect(jsonPath("$.status").value("WAITING"));

        // The analysis only looks at a holding that is Analysing, so being asked for it shows it was started.
        verify(sec, timeout(5000)).companyForTicker("NEWCO");
        awaitStatus("NEWCO", HoldingStatus.NO_REPORT_FOUND);
        list(portfolioId).andExpect(jsonPath("$[0].status").value("NO_REPORT_FOUND"));
    }

    @Test
    void aHoldingAddedWithNoWeightHasNone() throws Exception {
        add(me, portfolioId, "{\"ticker\": \"NOWEIGHT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weight").value(nullValue()));

        // Let its analysis finish, so it cannot spill into the next test.
        awaitStatus("NOWEIGHT", HoldingStatus.NO_REPORT_FOUND);
    }

    @Test
    void anAnalysisAlreadyRunningIsNotAnErrorAndTheNewHoldingWaitsForTheNextAnalyse() throws Exception {
        Holding running = held("RUNNING", "5");
        running.setStatus(HoldingStatus.ANALYSING);
        holdings.save(running);

        add(me, portfolioId, "{\"ticker\": \"LATER\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));

        verify(sec, after(500).never()).companyForTicker("LATER");
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId))
                .filteredOn(holding -> holding.getTicker().equals("LATER"))
                .singleElement()
                .satisfies(holding -> assertThat(holding.getStatus()).isEqualTo(HoldingStatus.WAITING));
    }

    @Test
    void aBlankOrTooLongTickerIsRefused() throws Exception {
        add(me, portfolioId, "{\"ticker\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a ticker."));
        add(me, portfolioId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a ticker."));
        add(me, portfolioId, "{\"ticker\": \"ABCDEFGHIJKLMNOPQRSTU\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("\"ABCDEFGHIJKLMNOPQRSTU\" is too long to be a ticker."));

        list(portfolioId).andExpect(jsonPath("$", hasSize(0)));
        verify(sec, never()).companyForTicker(anyString());
    }

    @Test
    void anAddedHoldingsWeightMustBeBetweenZeroAndOneHundredToo() throws Exception {
        add(me, portfolioId, "{\"ticker\": \"HEAVY\", \"weight\": 150}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The weight must be between 0 and 100."));

        list(portfolioId).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Acceptance Criterion 4: a ticker already held ---------------------------------------------------------

    @Test
    void addingATickerAlreadyHeldIsRefusedWith409AndTheMessage() throws Exception {
        held("AMD", "10");

        add(me, portfolioId, "{\"ticker\": \"AMD\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AMD is already in this portfolio."));
        // Capitals do not make it a different ticker.
        add(me, portfolioId, "{\"ticker\": \"amd\", \"weight\": 5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AMD is already in this portfolio."));

        list(portfolioId).andExpect(jsonPath("$", hasSize(1)));
        verify(sec, never()).companyForTicker(anyString());
    }

    // ---- Acceptance Criterion 5: somebody else's portfolio ----------------------------------------------------

    @Test
    void anotherUsersPortfolioAnswers404ToAllThree() throws Exception {
        Holding holding = held("NVDA", "10");
        String stranger = logins.bearer("stranger-" + UUID.randomUUID() + "@example.com");

        add(stranger, portfolioId, "{\"ticker\": \"AMD\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        changeWeight(stranger, portfolioId, holding.getId(), "{\"weight\": 50}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
        mockMvc.perform(delete("/api/portfolios/{id}/holdings/{holdingId}", portfolioId, holding.getId())
                        .header("Authorization", stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));

        // Nothing changed.
        list(portfolioId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].weight").value(10.0));
        verify(sec, never()).companyForTicker(anyString());
    }

    @Test
    void aHoldingFromAnotherPortfolioIsNotFoundEvenInMyOwn() throws Exception {
        Portfolio elsewhere = portfolios.save(new Portfolio(userId, "Elsewhere"));
        Holding theirs = holdings.save(new Holding(elsewhere.getId(), "XOM", new BigDecimal("10")));

        changeWeight(me, portfolioId, theirs.getId(), "{\"weight\": 50}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Holding not found."));
        mockMvc.perform(delete("/api/portfolios/{id}/holdings/{holdingId}", portfolioId, theirs.getId())
                        .header("Authorization", me))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Holding not found."));

        assertThat(holdings.findById(theirs.getId())).isPresent();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private Holding held(String ticker, String weight) {
        return holdings.save(new Holding(portfolioId, ticker, weight == null ? null : new BigDecimal(weight)));
    }

    private Alert alert(Company company, String exposure) {
        NewsArticle article = articles.save(new NewsArticle(company.getId(), "https://example.com/" + UUID.randomUUID(),
                "Set by the test: an article", "Test", Instant.now().truncatedTo(ChronoUnit.MICROS)));
        return alerts.save(new Alert(portfolioId, article.getId(), new BigDecimal(exposure)));
    }

    private ResultActions list(long portfolio) throws Exception {
        return mockMvc.perform(get("/api/portfolios/{id}/holdings", portfolio).header("Authorization", me));
    }

    private ResultActions add(String bearer, long portfolio, String body) throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/holdings", portfolio)
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeWeight(String bearer, long portfolio, long holdingId, String body) throws Exception {
        return mockMvc.perform(put("/api/portfolios/{id}/holdings/{holdingId}", portfolio, holdingId)
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Waits for the background analysis to give the holding with this ticker its result. */
    private void awaitStatus(String ticker, HoldingStatus status) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            boolean there = holdings.findByPortfolioIdOrderByTickerAsc(portfolioId).stream()
                    .anyMatch(holding -> holding.getTicker().equals(ticker) && holding.getStatus() == status);
            if (there) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(portfolioId))
                .filteredOn(holding -> holding.getTicker().equals(ticker))
                .singleElement()
                .satisfies(holding -> assertThat(holding.getStatus()).isEqualTo(status));
    }
}
