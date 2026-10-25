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
                .andExpect(jsonPath("$[?(@.id == " + recommendation.getId() + ")].text")
                        .value(List.of("Check the delivery status.")))
                .andExpect(jsonPath("$[?(@.id == " + recommendation.getId() + ")].event.title")
                        .value(List.of(HEADLINE)));

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

    private Recommendation pendingRecommendation() {
        Company nvidia = companyDirectory.findOrCreate("NVIDIA Corporation", "0001045810", "NVDA", List.of("NVIDIA"));
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
