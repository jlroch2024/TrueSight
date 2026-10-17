package com.truesight.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Test
    void theHandLabelsCoverTheTwentyHeadlinesAndTheReadmeStatesHowManyGeminiGotRight() throws Exception {
        List<String> headlines = firstTwentyHeadlines();
        List<List<String>> rows = Examples.newsLabels().lines().map(NewsExamplesTest::csvRow).toList();
        assertThat(rows.getFirst()).containsExactly("index", "title", "label", "labelled_by");

        JsonNode gemini = json.readTree(json.readTree(Examples.geminiNewsAssessment())
                .path("candidates").path(0).path("content").path("parts").path(0).path("text").asText());
        List<List<String>> labels = rows.subList(1, rows.size());
        assertThat(labels).hasSize(20);
        int agreed = 0;
        for (int i = 0; i < 20; i++) {
            List<String> row = labels.get(i);
            assertThat(row.get(0)).isEqualTo(String.valueOf(i + 1));
            assertThat(row.get(1)).isEqualTo(headlines.get(i));
            assertThat(row.get(2)).isIn("event", "noise");
            assertThat(row.get(3).split(";")).as("two teammates agree each label").hasSize(2);
            String geminiLabel = gemini.get(i).path("isEvent").asBoolean() ? "event" : "noise";
            if (geminiLabel.equals(row.get(2))) agreed++;
        }

        Matcher stated = Pattern.compile("Gemini agreed with (\\d+) of 20").matcher(Examples.readme());
        assertThat(stated.find()).as("the README states Gemini's accuracy").isTrue();
        assertThat(Integer.parseInt(stated.group(1))).isEqualTo(agreed);
    }

    /** The titles of the first 20 items in the saved Google News response, exactly as Gemini was sent them. */
    private static List<String> firstTwentyHeadlines() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(
                Examples.googleNewsTsmc().getBytes(StandardCharsets.UTF_8)));
        var items = document.getElementsByTagName("item");
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            var title = ((org.w3c.dom.Element) items.item(i)).getElementsByTagName("title").item(0);
            titles.add(title.getTextContent().trim());
        }
        return titles;
    }

    /** One CSV line's cells. A cell in "double quotes" may hold commas, and "" inside it is one quote. */
    private static List<String> csvRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted && c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }
}
