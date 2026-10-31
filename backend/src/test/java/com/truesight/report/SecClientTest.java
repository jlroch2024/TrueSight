package com.truesight.report;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

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

    // The bodies below have the shape of the SEC's submissions response (parallel lists under filings.recent, newest
    // first). Their values are made up to exercise the code; docs/examples holds no saved SEC response yet.

    private SecClient secAnswering(String cik, String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.sec.gov/submissions/CIK%s.json".formatted(cik)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new SecClient(builder.build(), JsonMapper.builder().build(), 10);
    }

    private static final String SUBMISSIONS = """
            {"filings": {"recent": {
              "form": ["8-K", "10-K", "10-Q", "10-K", "NT 10-K", "10-K", "10-K"],
              "accessionNumber": ["0000000001-26-000007", "0000000001-26-000006", "0000000001-25-000005",
                                  "0000000001-25-000004", "0000000001-24-000003", "0000000001-24-000002",
                                  "0000000001-23-000001"],
              "filingDate": ["2026-05-01", "2026-02-20", "2025-11-01", "2025-02-19", "2024-04-01", "2024-02-21",
                             "2023-02-22"],
              "primaryDocument": ["a.htm", "b.htm", "c.htm", "d.htm", "e.htm", "f.htm", "g.htm"]
            }}}""";

    @Test
    void annualFilingsAreTheNewestTenKsInTheOrderTheSecListsThemWithoutOtherForms() {
        SecClient sec = secAnswering("0000001234", SUBMISSIONS);

        List<SecClient.SecFiling> filings = sec.annualFilings("0000001234", 3);

        assertThat(filings).extracting(SecClient.SecFiling::accessionNumber)
                .containsExactly("0000000001-26-000006", "0000000001-25-000004", "0000000001-24-000002");
        assertThat(filings.get(0).filingDate()).hasToString("2026-02-20");
        assertThat(filings.get(0).form()).isEqualTo("10-K");
        assertThat(filings.get(0).url())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/1234/000000000126000006/b.htm");
    }

    @Test
    void annualFilingsStopAtTheCountAskedFor() {
        SecClient sec = secAnswering("0000001234", SUBMISSIONS);

        assertThat(sec.annualFilings("0000001234", 2)).hasSize(2);
    }

    @Test
    void aCompanyWithFewerAnnualFilingsThanAskedForGetsOnlyThose() {
        SecClient sec = secAnswering("0000001234", SUBMISSIONS);

        assertThat(sec.annualFilings("0000001234", 10)).hasSize(4);
    }

    @Test
    void theLatestAnnualFilingIsStillTheFirstOfThem() {
        SecClient sec = secAnswering("0000001234", SUBMISSIONS);

        assertThat(sec.latestAnnualFiling("0000001234").accessionNumber()).isEqualTo("0000000001-26-000006");
    }
}
