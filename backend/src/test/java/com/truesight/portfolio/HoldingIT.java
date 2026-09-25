package com.truesight.portfolio;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload a Portfolio CSV, through the whole app and a real database. Each test logs in as a brand-new user with one
 * empty portfolio.
 */
@IntegrationTest
class HoldingIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestLogins logins;

    @Autowired
    PortfolioRepository portfolios;

    String me;
    long portfolioId;

    @BeforeEach
    void logInWithAnEmptyPortfolio() {
        String email = "pm-" + UUID.randomUUID() + "@example.com";
        me = logins.bearer(email);
        portfolioId = portfolios.save(new Portfolio(logins.user(email).getId(), "Tech")).getId();
    }

    @Test
    void uploadingTheSampleFileIntoAnEmptyPortfolioGivesItsTenHoldings() throws Exception {
        upload(portfolioId, "holdings.csv", Files.readAllBytes(Path.of("../docs/examples/holdings.csv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].ticker").value("AMD"))
                .andExpect(jsonPath("$[0].weight").value(10.0))
                .andExpect(jsonPath("$[0].status").value("WAITING"));

        list(portfolioId).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void uploadingIntoAPortfolioWithHoldingsReplacesThem() throws Exception {
        upload(portfolioId, "old.csv", "ticker\nIBM\nORCL\n".getBytes(StandardCharsets.UTF_8));

        upload(portfolioId, "new.csv", "symbol,weight\nAAPL,60%\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk());

        list(portfolioId)
                .andExpect(jsonPath("$[*].ticker", contains("AAPL")))
                .andExpect(jsonPath("$[0].weight").value(60.0));
    }

    @Test
    void aCsvWithNoTickerColumnIsRefusedAndTheOldHoldingsStay() throws Exception {
        upload(portfolioId, "old.csv", "ticker\nIBM\n".getBytes(StandardCharsets.UTF_8));

        upload(portfolioId, "bad.csv", "company,weight\nApple,10\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file needs a ticker column."));

        list(portfolioId).andExpect(jsonPath("$[*].ticker", contains("IBM")));
    }

    @Test
    void aFileThatIsNotACsvIsRefused() throws Exception {
        upload(portfolioId, "holdings.xlsx", "ticker\nAAPL\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The file must be a CSV."));

        list(portfolioId).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anUploadWithNoFileIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/portfolios/" + portfolioId + "/holdings/upload").header("Authorization", me))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneUserCannotSeeOrReplaceAnotherUsersHoldings() throws Exception {
        upload(portfolioId, "mine.csv", "ticker\nIBM\n".getBytes(StandardCharsets.UTF_8));
        String someoneElse = logins.bearer("other-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/holdings").header("Authorization", someoneElse))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/portfolios/" + portfolioId + "/holdings/upload")
                        .file(csv("theirs.csv", "ticker\nAAPL\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", someoneElse))
                .andExpect(status().isNotFound());

        list(portfolioId).andExpect(jsonPath("$[*].ticker", contains("IBM")));
    }

    @Test
    void eachPortfolioShowsItsOwnHoldings() throws Exception {
        long other = portfolios.save(new Portfolio(portfolios.findById(portfolioId).orElseThrow().getUserId(),
                "Energy")).getId();
        upload(portfolioId, "tech.csv", "ticker\nNVDA\n".getBytes(StandardCharsets.UTF_8));
        upload(other, "energy.csv", "ticker\nXOM\n".getBytes(StandardCharsets.UTF_8));

        list(portfolioId).andExpect(jsonPath("$[*].ticker", contains("NVDA")));
        list(other).andExpect(jsonPath("$[*].ticker", contains("XOM")));
    }

    @Test
    void nobodyCanSeeHoldingsWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/api/portfolios/" + portfolioId + "/holdings")).andExpect(status().isUnauthorized());
    }

    private static MockMultipartFile csv(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "text/csv", content);
    }

    private ResultActions upload(long id, String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/portfolios/" + id + "/holdings/upload")
                .file(csv(filename, content))
                .header("Authorization", me));
    }

    private ResultActions list(long id) throws Exception {
        return mockMvc.perform(get("/api/portfolios/" + id + "/holdings").header("Authorization", me));
    }
}
