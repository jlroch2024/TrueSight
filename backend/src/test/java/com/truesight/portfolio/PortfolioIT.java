package com.truesight.portfolio;

import com.jayway.jsonpath.JsonPath;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manage My Portfolios, through the whole app and a real database. One test (or more) per Acceptance Criterion.
 *
 * <p>Each test logs in as a brand-new user, so it starts with no portfolios and never sees another test's data.
 */
@IntegrationTest
class PortfolioIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PortfolioRepository portfolios;

    @Autowired
    HoldingRepository holdings;

    @Autowired
    TestLogins logins;

    String me;

    @BeforeEach
    void logInAsANewUser() {
        me = logins.bearer("pm-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void nobodyCanUseThePortfoliosWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/api/portfolios")).andExpect(status().isUnauthorized());
    }

    @Test
    void aNewUserHasNoPortfolios() throws Exception {
        mockMvc.perform(get("/api/portfolios").header("Authorization", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aUserCanCreateAPortfolioByGivingItAName() throws Exception {
        create("Tech Book")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Tech Book"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/portfolios").header("Authorization", me))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Tech Book"));
    }

    @Test
    void creatingAPortfolioWithNoNameIsRefused() throws Exception {
        create("   ").andExpect(status().isBadRequest());
    }

    @Test
    void creatingAPortfolioWithANameAlreadyInUseIsRefused() throws Exception {
        create("Tech Book").andExpect(status().isCreated());

        create("Tech Book")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a portfolio with this name."));
    }

    @Test
    void aUserCanRenameAPortfolio() throws Exception {
        long id = createdId("Tech Book");

        rename(id, "Energy Book")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Energy Book"));

        assertThat(portfolios.findById(id).orElseThrow().getName()).isEqualTo("Energy Book");
    }

    @Test
    void renamingAPortfolioToANameAlreadyInUseIsRefused() throws Exception {
        createdId("Energy Book");
        long id = createdId("Tech Book");

        rename(id, "Energy Book")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a portfolio with this name."));
    }

    @Test
    void deletingAPortfolioRemovesItAndItsHoldings() throws Exception {
        long id = createdId("Tech Book");
        holdings.save(new Holding(id, "NVDA", new BigDecimal("60")));

        mockMvc.perform(delete("/api/portfolios/" + id).header("Authorization", me)).andExpect(status().isNoContent());

        assertThat(portfolios.findById(id)).isEmpty();
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(id)).isEmpty();
    }

    @Test
    void eachPortfolioKeepsItsOwnHoldings() throws Exception {
        long tech = createdId("Tech Book");
        long energy = createdId("Energy Book");
        holdings.save(new Holding(tech, "NVDA", null));
        holdings.save(new Holding(energy, "XOM", null));

        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(tech)).extracting(Holding::getTicker)
                .containsExactly("NVDA");
        assertThat(holdings.findByPortfolioIdOrderByTickerAsc(energy)).extracting(Holding::getTicker)
                .containsExactly("XOM");
    }

    @Test
    void anotherUsersPortfolioCannotBeSeenRenamedOrDeleted() throws Exception {
        Long other = logins.user("other-" + UUID.randomUUID() + "@example.com").getId();
        long theirs = portfolios.save(new Portfolio(other, "Their Book")).getId();

        mockMvc.perform(get("/api/portfolios").header("Authorization", me)).andExpect(jsonPath("$", hasSize(0)));
        rename(theirs, "Mine Now").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/portfolios/" + theirs).header("Authorization", me)).andExpect(status().isNotFound());

        assertThat(portfolios.findById(theirs).orElseThrow().getName()).isEqualTo("Their Book");
    }

    @Test
    void aPortfolioThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/portfolios/999999999").header("Authorization", me))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found."));
    }

    private ResultActions create(String name) throws Exception {
        return mockMvc.perform(post("/api/portfolios")
                .header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}"));
    }

    private ResultActions rename(long id, String name) throws Exception {
        return mockMvc.perform(put("/api/portfolios/" + id)
                .header("Authorization", me)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}"));
    }

    private long createdId(String name) throws Exception {
        String body = create(name).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
