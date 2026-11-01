package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

/** Analyst recommendation decisions and role checks through the real HTTP API. */
@IntegrationTest
class RecommendationsIT {
    private static final String HEADLINE = "GPUs from stolen $470,000 Nvidia RTX 5090 shipment at Brazil airport appear "
            + "for sale online, identified by serial numbers — three employees identified by police helped steal equipment";
    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired NewsArticleRepository articles;
    @Autowired NewsAssessmentRepository assessments;
    @Autowired RecommendationRepository recommendations;

    @Test
    void anAnalystCanEditAndApprovePendingRecommendations() throws Exception {
        Recommendation recommendation = pendingRecommendation();
        String analyst = "recommendation-review-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(get("/api/recommendations?status=PENDING")
                        .header("Authorization", logins.analystBearer(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(recommendation.getId().intValue())))
                .andExpect(jsonPath("$[*].text").value(hasItem("Check the delivery status.")))
                .andExpect(jsonPath("$[*].event.title").value(hasItem(HEADLINE)));

        mockMvc.perform(put("/api/recommendations/{id}", recommendation.getId())
                        .header("Authorization", logins.analystBearer(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","text":"Check carrier updates for the affected shipment.","note":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.editedText").value("Check carrier updates for the affected shipment."));
    }

    @Test
    void withholdingRequiresANote() throws Exception {
        Recommendation recommendation = pendingRecommendation();
        mockMvc.perform(put("/api/recommendations/{id}", recommendation.getId())
                        .header("Authorization", logins.analystBearer("recommendation-note-" + UUID.randomUUID()
                                + "@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"WITHHELD\",\"text\":null,\"note\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPortfolioManagerCannotListOrDecideRecommendations() throws Exception {
        Recommendation recommendation = pendingRecommendation();
        String manager = "recommendation-manager-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(get("/api/recommendations?status=PENDING")
                        .header("Authorization", logins.bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/recommendations/{id}", recommendation.getId())
                        .header("Authorization", logins.bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"text\":null,\"note\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theTabListsOnlyTheOlderOfTwoPendingSuggestionsForOneEvent() throws Exception {
        Company nvidia = uniqueCompany();
        Instant now = Instant.now();
        Recommendation older = pendingFor(nvidia, now.minusSeconds(3600));
        Recommendation newer = pendingFor(nvidia, now.minusSeconds(7200));

        mockMvc.perform(get("/api/recommendations?status=PENDING")
                        .header("Authorization", logins.analystBearer("recommendation-event-" + UUID.randomUUID()
                                + "@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(older.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(hasItem(newer.getId().intValue()))));
    }

    private Company uniqueCompany() {
        String id = UUID.randomUUID().toString().replace("-", "");
        return companyDirectory.findOrCreate("Event Test " + id, String.format("%010d", Math.abs(id.hashCode())),
                null, List.of());
    }

    private Recommendation pendingFor(Company company, Instant publishedAt) {
        NewsArticle article = articles.save(new NewsArticle(company.getId(),
                "https://example.com/recommendation-event/" + UUID.randomUUID(), HEADLINE, null, publishedAt));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.CYBER_ATTACK, Severity.MEDIUM,
                Confidence.HIGH, "Set by the test.", "Set by the test."));
        return recommendations.saveAndFlush(new Recommendation(article.getId(), "Check the delivery status."));
    }

    private Recommendation pendingRecommendation() {
        Company nvidia = uniqueCompany();
        NewsArticle article = articles.save(new NewsArticle(nvidia.getId(),
                "https://example.com/recommendation-test/" + UUID.randomUUID(),
                HEADLINE, null, Instant.now()));
        assessments.save(new NewsAssessment(article.getId(), true, EventType.OTHER_DISRUPTION, Severity.MEDIUM,
                Confidence.HIGH,
                "A $470,000 shipment of Nvidia RTX 5090 GPUs was stolen from an airport in Brazil with the help of employees.",
                "The headline reports the theft of a specific shipment of graphics cards, directly disrupting transit/delivery of sold goods."));
        return recommendations.save(new Recommendation(article.getId(), "Check the delivery status."));
    }
}
