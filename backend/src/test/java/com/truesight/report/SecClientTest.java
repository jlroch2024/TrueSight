package com.truesight.report;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
}
