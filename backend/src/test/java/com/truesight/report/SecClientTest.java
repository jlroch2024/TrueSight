package com.truesight.report;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    // The SEC's real submissions response for NVIDIA, saved unchanged in docs/examples (see its README).

    private SecClient secAnswering(String cik, String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.sec.gov/submissions/CIK%s.json".formatted(cik)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new SecClient(builder.build(), JsonMapper.builder().build(), 10);
    }

    private static final String NVIDIA_CIK = "0001045810";

    @Test
    void annualFilingsAreNvidiasNewestTenKsInTheOrderTheSecListsThem() {
        SecClient sec = secAnswering(NVIDIA_CIK, Examples.secSubmissionsNvidia());

        List<SecClient.SecFiling> filings = sec.annualFilings(NVIDIA_CIK, 3);

        assertThat(filings).hasSize(3);
        assertThat(filings).extracting(SecClient.SecFiling::form).containsOnly("10-K");
        assertThat(filings.get(0).accessionNumber()).isEqualTo("0001045810-26-000021");
        assertThat(filings.get(0).filingDate()).hasToString("2026-02-25");
        assertThat(filings.get(1).accessionNumber()).isEqualTo("0001045810-25-000023");
        assertThat(filings).extracting(SecClient.SecFiling::filingDate).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(filings.get(0).url())
                .startsWith("https://www.sec.gov/Archives/edgar/data/1045810/000104581026000021/");
    }

    @Test
    void annualFilingsStopAtTheCountAskedFor() {
        SecClient sec = secAnswering(NVIDIA_CIK, Examples.secSubmissionsNvidia());

        assertThat(sec.annualFilings(NVIDIA_CIK, 2)).hasSize(2);
    }

    @Test
    void askingForManyAnnualFilingsGivesEveryOneInTheListNewestFirstAndNothingElse() {
        SecClient sec = secAnswering(NVIDIA_CIK, Examples.secSubmissionsNvidia());

        List<SecClient.SecFiling> filings = sec.annualFilings(NVIDIA_CIK, 1000);

        assertThat(filings.size()).isGreaterThanOrEqualTo(3);
        assertThat(filings).extracting(SecClient.SecFiling::form).isSubsetOf("10-K", "20-F");
        assertThat(filings).extracting(SecClient.SecFiling::filingDate).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void theLatestAnnualFilingIsStillTheFirstOfThem() {
        SecClient sec = secAnswering(NVIDIA_CIK, Examples.secSubmissionsNvidia());

        assertThat(sec.latestAnnualFiling(NVIDIA_CIK).accessionNumber()).isEqualTo("0001045810-26-000021");
    }
}
