package com.truesight.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the saved news fixtures have the agreed shape without calling Google News or Gemini. */
class NewsExamplesTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void theSavedGoogleNewsResponseContainsTheHeadlinesUsedByTheAssessment() throws Exception {
        var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        var document = builder.parse(new ByteArrayInputStream(
                Examples.googleNewsTsmc().getBytes(StandardCharsets.UTF_8)));

        assertThat(document.getElementsByTagName("item").getLength()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void theSavedGeminiResponseContainsOneAssessmentForEachOfTheFirstTwentyHeadlines() throws Exception {
        JsonNode response = json.readTree(Examples.geminiNewsAssessment());
        String answer = response.path("candidates").path(0).path("content").path("parts").path(0)
                .path("text").asText();
        JsonNode assessments = json.readTree(answer);

        assertThat(assessments).hasSize(20);
        for (int index = 0; index < 20; index++) {
            assertThat(assessments.get(index).path("index").asInt()).isEqualTo(index + 1);
        }
    }
}
