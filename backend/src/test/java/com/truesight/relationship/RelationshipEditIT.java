package com.truesight.relationship;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The acceptance criteria of Fix or Add a Relationship, through the whole app and a real database. */
@IntegrationTest
class RelationshipEditIT {

    private static final String MEMORY_SENTENCE =
            "We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyDisplayNames displayNames;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void editingProvidesChangesItEverywhereAndRecordsWhoAndWhen() throws Exception {
        Fixture fixture = fixture();
        Relationship relationship = relationships.save(new Relationship(
                fixture.report().getId(), fixture.company().getId(),
                companyDirectory.findOrCreate("SK Hynix Inc.", null, null, List.of()).getId(),
                RelationshipType.SUPPLIER, "memory", MEMORY_SENTENCE));
        String email = "relationship-editor-" + UUID.randomUUID() + "@example.com";
        String analyst = logins.analystBearer(email);
        String owner = "relationship-edit-owner-" + UUID.randomUUID() + "@example.com";
        Portfolio portfolio = portfolioHolding(owner, fixture.company());

        mockMvc.perform(put("/api/relationships/{id}", relationship.getId())
                        .header("Authorization", analyst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provides\":\"graphics memory\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationship.provides").value("graphics memory"));

        mockMvc.perform(get("/api/reviews").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relationship.id == %s)].relationship.provides"
                        .formatted(relationship.getId())).value(contains("graphics memory")));
        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].provides".formatted(relationship.getId()))
                        .value(contains("graphics memory")));

        Relationship saved = relationships.findById(relationship.getId()).orElseThrow();
        assertThat(saved.getEditedBy()).isEqualTo(logins.user(email).getId());
        assertThat(saved.getEditedAt()).isNotNull();
        assertThat(saved.getEvidence()).isEqualTo(MEMORY_SENTENCE);
    }

    @Test
    void addingAReportSentenceSavesTheReportsWordsApprovedAndAddedByTheAnalyst() throws Exception {
        Fixture fixture = fixture();
        String email = "relationship-adder-" + UUID.randomUUID() + "@example.com";
        String analyst = logins.analystBearer(email);

        mockMvc.perform(get("/api/companies?withReport=true").header("Authorization", analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].name".formatted(fixture.company().getId()))
                        .value(contains(fixture.company().getName())))
                .andExpect(jsonPath("$[?(@.id == %s)].displayName".formatted(fixture.company().getId()))
                        .value(contains(displayNames.of(fixture.company()))));
        String body = add(analyst, fixture.company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationship.evidence").value(MEMORY_SENTENCE))
                .andExpect(jsonPath("$.relationship.source").value("ANALYST"))
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();
        long relationshipId = json.readTree(body).path("relationship").path("id").asLong();

        Relationship saved = relationships.findById(relationshipId).orElseThrow();
        assertThat(saved.getEvidence()).isEqualTo(MEMORY_SENTENCE);
        assertThat(saved.getSource()).isEqualTo(RelationshipSource.ANALYST);
        assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(saved.getAddedBy()).isEqualTo(logins.user(email).getId());
        assertThat(saved.getReviewedBy()).isEqualTo(logins.user(email).getId());
    }

    /** As for relationships the AI finds (See How Confident TrueSight Is), so none added today reads "Not Recorded". */
    @Test
    void anAddedRelationshipRecordsHowMuchOfTheAnalystsSentenceIsInTheReport() throws Exception {
        String exactBody = add(analyst(), fixture().company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationship.confidence.reasons[1].text").value("Strong Quote Match (100%)"))
                .andReturn().getResponse().getContentAsString();
        // The real sentence's 12 words with "purchase" changed to "buy": 11 of 12 found, recorded rounded down.
        String reworded = "We buy memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.";
        String rewordedBody = add(analyst(), fixture().company(), "SK Hynix Inc.", reworded)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relationship.evidence").value(MEMORY_SENTENCE))
                .andExpect(jsonPath("$.relationship.confidence.reasons[1].text").value("Partial Quote Match (91%)"))
                .andReturn().getResponse().getContentAsString();

        assertThat(relationships.findById(json.readTree(exactBody).path("relationship").path("id").asLong())
                .orElseThrow().getQuoteMatch()).isEqualByComparingTo("1.000");
        assertThat(relationships.findById(json.readTree(rewordedBody).path("relationship").path("id").asLong())
                .orElseThrow().getQuoteMatch()).isEqualByComparingTo("0.916");
    }

    @Test
    void aSentenceThatIsNotInTheReportIsRefusedAndNothingIsSaved() throws Exception {
        Fixture fixture = fixture();
        int before = relationships.findByCompanyIdIn(List.of(fixture.company().getId())).size();

        add(analyst(), fixture.company(), "SK Hynix Inc.", "ABSENT_SENTENCE_MARKER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "That sentence is not in " + displayNames.of(fixture.company()) + "'s annual report."));

        assertThat(relationships.findByCompanyIdIn(List.of(fixture.company().getId()))).hasSize(before);
    }

    @Test
    void addingTheSameCompaniesTypeAndEvidenceTwiceIsRefused() throws Exception {
        Fixture fixture = fixture();
        String analyst = analyst();

        add(analyst, fixture.company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isCreated());
        add(analyst, fixture.company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That relationship is already recorded."));
    }

    @Test
    void anAddedRelationshipAppearsOnTheCompanysPortfolioGraphWithItsAnalystSource() throws Exception {
        Fixture fixture = fixture();
        String owner = "relationship-graph-owner-" + UUID.randomUUID() + "@example.com";
        Portfolio portfolio = portfolioHolding(owner, fixture.company());
        String body = add(analyst(), fixture.company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long relationshipId = json.readTree(body).path("relationship").path("id").asLong();

        mockMvc.perform(get("/api/portfolios/{id}/relationships", portfolio.getId())
                        .header("Authorization", logins.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].source".formatted(relationshipId))
                        .value(contains("ANALYST")));
    }

    @Test
    void portfolioManagersGetA403FromEditingAddingAndListingCompaniesWithReports() throws Exception {
        Fixture fixture = fixture();
        Relationship relationship = relationships.save(new Relationship(
                fixture.report().getId(), fixture.company().getId(),
                companyDirectory.findOrCreate("Micron Technology, Inc.", null, null, List.of()).getId(),
                RelationshipType.SUPPLIER, "memory", MEMORY_SENTENCE));
        String manager = logins.bearer("relationship-manager-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(put("/api/relationships/{id}", relationship.getId())
                        .header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provides\":\"memory\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));
        add(manager, fixture.company(), "SK Hynix Inc.", MEMORY_SENTENCE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));
        mockMvc.perform(get("/api/companies?withReport=true").header("Authorization", manager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only analysts can do this."));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        Company company = companyDirectory.findOrCreate("NVIDIA Relationship Test " + suffix, null, "NVDA", List.of());
        Report report = reports.save(new Report(company.getId(), "10-K",
                "re-" + suffix.replace("-", "").substring(0, 20), LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/Archives/relationship-test/" + suffix, Examples.nvidia10K()));
        return new Fixture(company, report);
    }

    private String analyst() {
        return logins.analystBearer("relationship-analyst-" + UUID.randomUUID() + "@example.com");
    }

    private ResultActions add(String bearer, Company company, String counterpartyName, String sentence) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "companyId", company.getId(),
                "counterpartyName", counterpartyName,
                "type", "SUPPLIER",
                "provides", "memory",
                "sentence", sentence));
        return mockMvc.perform(post("/api/relationships")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Portfolio portfolioHolding(String email, Company company) {
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "P " + UUID.randomUUID()));
        Holding holding = new Holding(portfolio.getId(), "NVDA", null);
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }

    private record Fixture(Company company, Report report) {
    }
}
