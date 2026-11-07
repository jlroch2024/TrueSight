package com.truesight.report;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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

    // ---- the ticker list: downloaded once a day, not once per ticker -----------------------------------------------

    // The body has the shape of the SEC's company_tickers.json (a number for each row, with cik_str, ticker and title).
    // The companies are made up: only how often the list is downloaded matters here.
    private static final String TICKERS = """
            {"0": {"cik_str": 1111, "ticker": "AAA", "title": "Test Company A"},
             "1": {"cik_str": 2222, "ticker": "BBB", "title": "Test Company B"}}""";
    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";

    /** A clock the test moves. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-30T09:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void manyTickersAreFoundFromOneDownloadOfTheList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        // Exactly one request is expected: a second would fail the test.
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        assertThat(sec.companyForTicker("AAA").cik()).isEqualTo("0000001111");
        assertThat(sec.companyForTicker("bbb").name()).isEqualTo("Test Company B");
        assertThat(sec.companyForTicker("ZZZ")).isNull();

        server.verify();
    }

    @Test
    void theListIsKeptForADay() {
        MovableClock clock = new MovableClock();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10, clock);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        sec.companyForTicker("AAA");
        clock.advance(Duration.ofHours(23));

        assertThat(sec.companyForTicker("BBB")).isNotNull();
        server.verify();
    }

    @Test
    void theListIsDownloadedAgainOnceItIsMoreThanADayOld() {
        MovableClock clock = new MovableClock();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10, clock);
        server.expect(ExpectedCount.times(2), requestTo(TICKERS_URL))
                .andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        sec.companyForTicker("AAA");
        clock.advance(Duration.ofHours(25));
        sec.companyForTicker("AAA");

        server.verify();
    }

    @Test
    void aListThatCouldNotBeDownloadedIsNotKeptSoTheNextTickerTriesAgain() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sec.companyForTicker("AAA")).isInstanceOf(SecClient.SecAccessException.class);
        assertThat(sec.companyForTicker("AAA")).isNotNull();

        server.verify();
    }

    @Test
    void concurrentTickersShareOneDownload() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(TICKERS, MediaType.APPLICATION_JSON));

        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<SecClient.SecCompany>> found = new ArrayList<>();
            for (int i = 0; i < 8; i++) found.add(callers.submit(() -> sec.companyForTicker("AAA")));
            for (Future<SecClient.SecCompany> company : found) assertThat(company.get()).isNotNull();

            server.verify();
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void twoDownloadsOverlapWhileStillStartingAtMostTenASecond() throws Exception {
        String url = "https://www.sec.gov/test-large-report";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        List<Long> startedAt = Collections.synchronizedList(new ArrayList<>());
        // Each download is held open until both have started: under one lock for the whole download, the second
        // could never start while the first was running, and this would time out.
        CountDownLatch bothDownloading = new CountDownLatch(2);
        server.expect(ExpectedCount.times(2), requestTo(url))
                .andExpect(request -> startedAt.add(System.nanoTime()))
                .andRespond(request -> {
                    bothDownloading.countDown();
                    try {
                        if (!bothDownloading.await(3, TimeUnit.SECONDS)) {
                            throw new AssertionError("The second download never started while the first was running.");
                        }
                    } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                    }
                    return withSuccess("report", MediaType.TEXT_PLAIN).createResponse(request);
                });

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = callers.submit(() -> sec.download(url));
            Future<String> second = callers.submit(() -> sec.download(url));
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("report");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("report");

            server.verify();
            List<Long> ordered = startedAt.stream().sorted().toList();
            assertThat(ordered.get(1) - ordered.get(0)).isGreaterThanOrEqualTo(100_000_000L);
        } finally {
            callers.shutdownNow();
        }
    }
}
