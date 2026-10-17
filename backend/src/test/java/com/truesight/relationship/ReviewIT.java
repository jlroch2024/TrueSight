package com.truesight.relationship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Acceptance Criteria of Review AI Findings, through the whole app and a real database.
 *
 * <p>The database is shared by every integration test, so other tests' relationships may be in the lists too. Each
 * test therefore builds its own companies, reports and relationships (with names that cannot clash) and looks for
 * them by id, never by position in a list it does not own.
 */
@IntegrationTest
class ReviewIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;

    private final JsonMapper json = JsonMapper.builder().build();

    // ---- building data -------------------------------------------------------------------------------------------

    private Company company(String name, String ticker) {
        return companyDirectory.findOrCreate(name + " " + UUID.randomUUID(), null, ticker, List.of());
    }

    /** A relationship from the holding's report: the counterparty supplies the holding. */
    private Relationship relationship(Company holding, Company counterparty, String filingDate) {
        Report report = reports.save(new Report(holding.getId(), "10-K", "rv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), LocalDate.parse(filingDate),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
        return relationships.save(new Relationship(report.getId(), holding.getId(), counterparty.getId(),
                RelationshipType.SUPPLIER, "semiconductor wafers", "We buy wafers from them."));
    }

    /** A portfolio of this user that holds these companies, already analysed. */
    private Portfolio portfolioHolding(String email, Company... held) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "P " + UUID.randomUUID()));
        for (Company company : held) {
            Holding holding = new Holding(portfolio.getId(), "T" + UUID.randomUUID().toString().substring(0, 6), null);
            holding.setCompanyId(company.getId());
            holding.setStatus(HoldingStatus.DONE);
            holdings.save(holding);
        }
        return portfolio;
    }

    // ---- calling the endpoints -----------------------------------------------------------------------------------

    private String analyst() {
        return logins.analystBearer("analyst-" + UUID.randomUUID() + "@example.com");
    }

    private JsonNode list(String bearer, String query) throws Exception {
        String body = mockMvc.perform(get("/api/reviews" + query).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode itemFor(JsonNode list, long relationshipId) {
        for (JsonNode item : list) {
            if (item.path("relationship").path("id").asLong() == relationshipId) {
                return item;
            }
        }
        return null;
    }

    private int positionOf(JsonNode list, long relationshipId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).path("relationship").path("id").asLong() == relationshipId) {
                return i;
            }
        }
        return -1;
    }

    private org.springframework.test.web.servlet.ResultActions decide(String bearer, long id, String body) throws Exception {
        return mockMvc.perform(put("/api/relationships/{id}/review", id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode graphOf(Portfolio portfolio, String email) throws Exception {
        String body = mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // ---- criterion 1 ---------------------------------------------------------------------------------------------

    @Test
    void anAnalystSeesEveryUnreviewedRelationshipWithEverythingNeededToJudgeIt() throws Exception {
        Company nvidia = company("Holding One", "HOLD");
        Company tsmc = company("Supplier One", "SUP");
        Relationship relationship = relationship(nvidia, tsmc, "2026-02-25");
        portfolioHolding("rv-one@example.com", nvidia);

        JsonNode item = itemFor(list(analyst(), "?status=UNREVIEWED"), relationship.getId());

        assertThat(item).isNotNull();
        assertThat(item.path("reviewStatus").asText()).isEqualTo("UNREVIEWED");
        assertThat(item.path("relationship").path("company").path("name").asText()).isEqualTo(nvidia.getName());
        assertThat(item.path("relationship").path("counterparty").path("name").asText()).isEqualTo(tsmc.getName());
        assertThat(item.path("relationship").path("type").asText()).isEqualTo("SUPPLIER");
        assertThat(item.path("relationship").path("provides").asText()).isEqualTo("semiconductor wafers");
        assertThat(item.path("relationship").path("evidence").asText()).isEqualTo("We buy wafers from them.");
        assertThat(item.path("relationship").path("report").path("form").asText()).isEqualTo("10-K");
        assertThat(item.path("relationship").path("report").path("filingDate").asText()).isEqualTo("2026-02-25");
        assertThat(item.path("relationship").path("report").path("url").asText()).startsWith("https://www.sec.gov/");
        assertThat(item.path("usedByPortfolios").asLong()).isEqualTo(1);
        assertThat(item.path("reviewedBy").isNull()).isTrue();
        assertThat(item.path("reviewedAt").isNull()).isTrue();
        assertThat(item.path("reviewNote").isNull()).isTrue();
    }

    @Test
    void theCountOfPortfoliosNeverSaysWhichPortfoliosOrWhose() throws Exception {
        Company holding = company("Holding Private", "PRV");
        Relationship relationship = relationship(holding, company("Supplier Private", "SPV"), "2026-02-25");
        portfolioHolding("rv-private@example.com", holding);

        JsonNode item = itemFor(list(analyst(), ""), relationship.getId());

        assertThat(item.toString()).doesNotContain("rv-private@example.com").doesNotContainIgnoringCase("portfolioId");
        assertThat(item.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("relationship", "reviewStatus", "reviewNote", "reviewedBy", "reviewedAt",
                        "usedByPortfolios");
    }

    // ---- criterion 2 ---------------------------------------------------------------------------------------------

    @Test
    void aRelationshipUsedByThreePortfoliosIsListedAboveOneUsedByOne() throws Exception {
        Company popular = company("Popular Holding", "POP");
        Company lonely = company("Lonely Holding", "LON");
        // The lonely one has the newer report, so only the count can put the popular one first.
        Relationship usedByThree = relationship(popular, company("Popular Supplier", "PS"), "2025-01-01");
        Relationship usedByOne = relationship(lonely, company("Lonely Supplier", "LS"), "2026-06-01");
        portfolioHolding("rv-order-1@example.com", popular);
        portfolioHolding("rv-order-2@example.com", popular);
        portfolioHolding("rv-order-3@example.com", popular, lonely);

        JsonNode all = list(analyst(), "?status=UNREVIEWED");

        assertThat(itemFor(all, usedByThree.getId()).path("usedByPortfolios").asLong()).isEqualTo(3);
        assertThat(itemFor(all, usedByOne.getId()).path("usedByPortfolios").asLong()).isEqualTo(1);
        assertThat(positionOf(all, usedByThree.getId())).isLessThan(positionOf(all, usedByOne.getId()));
    }

    @Test
    void aPortfolioHoldingBothCompaniesOfARelationshipIsCountedOnce() throws Exception {
        Company holding = company("Both Held", "BH");
        Company counterparty = company("Both Held Supplier", "BHS");
        Relationship relationship = relationship(holding, counterparty, "2026-02-25");
        portfolioHolding("rv-both@example.com", holding, counterparty);

        assertThat(itemFor(list(analyst(), ""), relationship.getId()).path("usedByPortfolios").asLong()).isEqualTo(1);
    }

    @Test
    void unreviewedRelationshipsComeBeforeReviewedOnesInTheFullList() throws Exception {
        Company holding = company("Order Holding", "ORD");
        Relationship decided = relationship(holding, company("Order Supplier A", "OA"), "2026-06-01");
        Relationship waiting = relationship(holding, company("Order Supplier B", "OB"), "2020-01-01");
        String bearer = analyst();
        decide(bearer, decided.getId(), "{\"decision\":\"APPROVED\"}").andExpect(status().isOk());

        JsonNode all = list(bearer, "");

        assertThat(positionOf(all, waiting.getId())).isLessThan(positionOf(all, decided.getId()));
    }

    // ---- criterion 3 ---------------------------------------------------------------------------------------------

    @Test
    void approvingMarksItApprovedWithTheAnalystsEmailAndTheTimeAndItLeavesTheUnreviewedList() throws Exception {
        Relationship relationship = relationship(company("Approve Holding", "APH"), company("Approve Supplier", "APS"), "2026-02-25");
        String email = "approver-" + UUID.randomUUID() + "@example.com";
        String bearer = logins.analystBearer(email);

        decide(bearer, relationship.getId(), "{\"decision\":\"APPROVED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(email))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty())
                .andExpect(jsonPath("$.relationship.id").value(relationship.getId()));

        assertThat(itemFor(list(bearer, "?status=UNREVIEWED"), relationship.getId())).isNull();
        assertThat(itemFor(list(bearer, "?status=APPROVED"), relationship.getId())).isNotNull();
    }

    @Test
    void anApprovalMayHaveANoteAndTheLatestDecisionCounts() throws Exception {
        Relationship relationship = relationship(company("Change Holding", "CH"), company("Change Supplier", "CS"), "2026-02-25");
        String bearer = analyst();

        decide(bearer, relationship.getId(), "{\"decision\":\"REJECTED\",\"note\":\"First thought it was wrong.\"}")
                .andExpect(status().isOk());
        decide(bearer, relationship.getId(), "{\"decision\":\"APPROVED\",\"note\":\"Checked the report again.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNote").value("Checked the report again."));
    }

    // ---- criteria 4, 5, 6 ----------------------------------------------------------------------------------------

    @Test
    void rejectingWithoutANoteIsRefused() throws Exception {
        Relationship relationship = relationship(company("NoNote Holding", "NN"), company("NoNote Supplier", "NS"), "2026-02-25");
        String bearer = analyst();

        decide(bearer, relationship.getId(), "{\"decision\":\"REJECTED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please say why this is rejected."));
        decide(bearer, relationship.getId(), "{\"decision\":\"REJECTED\",\"note\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please say why this is rejected."));

        assertThat(relationships.findById(relationship.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(ReviewStatus.UNREVIEWED);
    }

    @Test
    void rejectingWithANoteMarksItRejectedAndItNoLongerAppearsOnAnyPortfoliosGraph() throws Exception {
        Company holding = company("Reject Holding", "RH");
        Relationship rejected = relationship(holding, company("Reject Supplier", "RS"), "2026-02-25");
        Relationship kept = relationship(holding, company("Kept Supplier", "KS"), "2026-02-25");
        Portfolio first = portfolioHolding("rv-graph-1@example.com", holding);
        Portfolio second = portfolioHolding("rv-graph-2@example.com", holding);

        decide(analyst(), rejected.getId(), "{\"decision\":\"REJECTED\",\"note\":\"Planned partner, not a supplier.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Planned partner, not a supplier."));

        for (JsonNode graph : List.of(graphOf(first, "rv-graph-1@example.com"), graphOf(second, "rv-graph-2@example.com"))) {
            assertThat(itemFor(wrap(graph), rejected.getId())).isNull();
            assertThat(itemFor(wrap(graph), kept.getId())).isNotNull();
        }
        // Nothing is deleted, so the rejection can be undone.
        assertThat(relationships.findById(rejected.getId())).isPresent();
    }

    @Test
    void changingARejectedRelationshipToApprovedBringsItBackOnTheGraph() throws Exception {
        Company holding = company("Undo Holding", "UH");
        Relationship relationship = relationship(holding, company("Undo Supplier", "US"), "2026-02-25");
        Portfolio portfolio = portfolioHolding("rv-undo@example.com", holding);
        String bearer = analyst();

        decide(bearer, relationship.getId(), "{\"decision\":\"REJECTED\",\"note\":\"Wrong.\"}").andExpect(status().isOk());
        assertThat(itemFor(wrap(graphOf(portfolio, "rv-undo@example.com")), relationship.getId())).isNull();

        decide(bearer, relationship.getId(), "{\"decision\":\"APPROVED\"}").andExpect(status().isOk());
        assertThat(itemFor(wrap(graphOf(portfolio, "rv-undo@example.com")), relationship.getId())).isNotNull();
    }

    @Test
    void aNoteOver500CharactersIsRefusedAndAnUnknownRelationshipIsNotFound() throws Exception {
        Relationship relationship = relationship(company("Long Holding", "LH"), company("Long Supplier", "LSP"), "2026-02-25");
        String bearer = analyst();

        decide(bearer, relationship.getId(), "{\"decision\":\"REJECTED\",\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        decide(bearer, 999_999_999L, "{\"decision\":\"APPROVED\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Relationship not found."));
    }

    @Test
    void theGraphDataCarriesTheReviewStatusTimeAndNote() throws Exception {
        Company holding = company("Fields Holding", "FLD");
        Relationship relationship = relationship(holding, company("Fields Supplier", "FSP"), "2026-02-25");
        Portfolio portfolio = portfolioHolding("rv-fields@example.com", holding);

        JsonNode before = itemFor(wrap(graphOf(portfolio, "rv-fields@example.com")), relationship.getId());
        assertThat(before.path("relationship").path("reviewStatus").asText()).isEqualTo("UNREVIEWED");
        assertThat(before.path("relationship").path("reviewedAt").isNull()).isTrue();
        assertThat(before.path("relationship").path("reviewNote").isNull()).isTrue();

        decide(analyst(), relationship.getId(), "{\"decision\":\"APPROVED\",\"note\":\"Checked.\"}").andExpect(status().isOk());

        JsonNode after = itemFor(wrap(graphOf(portfolio, "rv-fields@example.com")), relationship.getId());
        assertThat(after.path("relationship").path("reviewStatus").asText()).isEqualTo("APPROVED");
        assertThat(after.path("relationship").path("reviewedAt").isNull()).isFalse();
        assertThat(after.path("relationship").path("reviewNote").asText()).isEqualTo("Checked.");
    }

    // ---- criterion 7 ---------------------------------------------------------------------------------------------

    @Test
    void theFilterShowsUnreviewedApprovedRejectedOrAll() throws Exception {
        Company holding = company("Filter Holding", "FH");
        Relationship unreviewed = relationship(holding, company("Filter Supplier U", "FU"), "2026-02-25");
        Relationship approved = relationship(holding, company("Filter Supplier A", "FA"), "2026-02-25");
        Relationship rejected = relationship(holding, company("Filter Supplier R", "FR"), "2026-02-25");
        String bearer = analyst();
        decide(bearer, approved.getId(), "{\"decision\":\"APPROVED\"}").andExpect(status().isOk());
        decide(bearer, rejected.getId(), "{\"decision\":\"REJECTED\",\"note\":\"No.\"}").andExpect(status().isOk());

        assertThat(shown(list(bearer, "?status=UNREVIEWED"), unreviewed, approved, rejected)).containsExactly(true, false, false);
        assertThat(shown(list(bearer, "?status=APPROVED"), unreviewed, approved, rejected)).containsExactly(false, true, false);
        assertThat(shown(list(bearer, "?status=REJECTED"), unreviewed, approved, rejected)).containsExactly(false, false, true);
        assertThat(shown(list(bearer, ""), unreviewed, approved, rejected)).containsExactly(true, true, true);

        mockMvc.perform(get("/api/reviews?status=NONSENSE").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    // ---- criterion 8 ---------------------------------------------------------------------------------------------

    @Test
    void aPortfolioManagerGetsA403FromBothEndpoints() throws Exception {
        Relationship relationship = relationship(company("Forbidden Holding", "FB"), company("Forbidden Supplier", "FS"), "2026-02-25");
        String manager = logins.bearer("rv-manager@example.com");

        mockMvc.perform(get("/api/reviews").header("Authorization", manager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));
        decide(manager, relationship.getId(), "{\"decision\":\"APPROVED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));

        assertThat(relationships.findById(relationship.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(ReviewStatus.UNREVIEWED);
    }

    @Test
    void withoutALoginTheAnswerIs401() throws Exception {
        mockMvc.perform(get("/api/reviews")).andExpect(status().isUnauthorized());
    }

    // ---- criterion 10 --------------------------------------------------------------------------------------------

    @Test
    void aDecisionIsSavedInTheDatabaseSoItSurvivesARestart() throws Exception {
        Relationship relationship = relationship(company("Saved Holding", "SH"), company("Saved Supplier", "SSP"), "2026-02-25");
        String email = "saver-" + UUID.randomUUID() + "@example.com";

        decide(logins.analystBearer(email), relationship.getId(), "{\"decision\":\"REJECTED\",\"note\":\"Not a supplier.\"}")
                .andExpect(status().isOk());

        // Read straight from the table, not from anything held in memory.
        Relationship saved = relationships.findById(relationship.getId()).orElseThrow();
        assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(saved.getReviewNote()).isEqualTo("Not a supplier.");
        assertThat(saved.getReviewedBy()).isEqualTo(logins.user(email).getId());
        assertThat(saved.getReviewedAt()).isNotNull();
    }

    // ---- helpers for the lists -----------------------------------------------------------------------------------

    private boolean[] shown(JsonNode list, Relationship... which) {
        boolean[] result = new boolean[which.length];
        for (int i = 0; i < which.length; i++) {
            result[i] = itemFor(list, which[i].getId()) != null;
        }
        return result;
    }

    /** The graph endpoint answers a list of relationships; wrap each so {@link #itemFor} can find it by id. */
    private JsonNode wrap(JsonNode graph) {
        var wrapped = json.createArrayNode();
        for (JsonNode relationship : graph) {
            wrapped.add(json.createObjectNode().set("relationship", relationship));
        }
        return wrapped;
    }
}
