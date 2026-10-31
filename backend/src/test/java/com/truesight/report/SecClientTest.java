package com.truesight.report;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SecClientTest {

    @Test
    void readsEachEightKWithItsItemsAcceptanceTimeAndAddressFromTheSubmissionsList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        server.expect(requestTo("https://data.sec.gov/submissions/CIK0000021076.json"))
                .andRespond(withSuccess(Examples.secSubmissionsClorox(), MediaType.APPLICATION_JSON));

        List<SecClient.SecCurrentReport> reports = sec.recentCurrentReports("0000021076");

        server.verify();
        assertThat(reports).isNotEmpty();
        assertThat(reports.getFirst()).isEqualTo(new SecClient.SecCurrentReport("0001206774-26-000586",
                List.of("5.02", "7.01", "9.01"), Instant.parse("2026-10-29T11:05:37Z"),
                "https://www.sec.gov/Archives/edgar/data/21076/000120677426000586/clx4708451-8k.htm"));
        assertThat(reports).filteredOn(report -> report.accessionNumber().equals("0001206774-23-000969"))
                .singleElement()
                .isEqualTo(new SecClient.SecCurrentReport("0001206774-23-000969", List.of("8.01", "9.01"),
                        Instant.parse("2023-08-14T21:29:29Z"),
                        "https://www.sec.gov/Archives/edgar/data/21076/000120677423000969/clx4231381-8k.htm"));
        // Only 8-Ks: no 10-K, 10-Q or Form 4, and no amendment (8-K/A).
        JsonNode recent = JsonMapper.builder().build().readTree(Examples.secSubmissionsClorox())
                .path("filings").path("recent");
        long eightKs = 0;
        for (JsonNode form : recent.path("form")) {
            if (form.asText().equals("8-K")) eightKs++;
        }
        assertThat(reports).hasSize((int) eightKs);
        // Its one amendment, an 8-K/A of 30 March 2018.
        assertThat(reports).extracting(SecClient.SecCurrentReport::accessionNumber)
                .doesNotContain("0001206774-18-001044");
    }

    @Test
    void concurrentCallersAreStillPacedAtNoMoreThanTenRequestsPerSecond() throws Exception {
        String url = "https://www.sec.gov/test-report";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        SecClient sec = new SecClient(client, JsonMapper.builder().build(), 10);
        List<Long> startedAt = Collections.synchronizedList(new ArrayList<>());
        server.expect(org.springframework.test.web.client.ExpectedCount.times(12), requestTo(url))
                .andExpect(request -> startedAt.add(System.nanoTime()))
                .andRespond(withSuccess("report", MediaType.TEXT_PLAIN));

        ExecutorService callers = Executors.newFixedThreadPool(12);
        try {
            List<Future<String>> responses = new ArrayList<>();
            for (int i = 0; i < 12; i++) responses.add(callers.submit(() -> sec.download(url)));
            for (Future<String> response : responses) assertThat(response.get()).isEqualTo("report");

            server.verify();
            assertThat(startedAt).hasSize(12);
            List<Long> ordered = startedAt.stream().sorted().toList();
            for (int i = 0; i + 10 < ordered.size(); i++) {
                assertThat(ordered.get(i + 10) - ordered.get(i)).isGreaterThanOrEqualTo(1_000_000_000L);
            }
        } finally {
            callers.shutdownNow();
        }
    }
}
