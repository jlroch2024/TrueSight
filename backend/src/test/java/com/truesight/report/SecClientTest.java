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
import java.util.concurrent.atomic.AtomicReference;

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

    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final List<String> TEN_TICKERS =
            List.of("NVDA", "AMD", "TSM", "ASML", "AAPL", "MSFT", "INTC", "QCOM", "AVGO", "MU");

    @Test
    void tenTickersAreLookedUpWithOneRequestForTheSecTickerList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(tickerList(), MediaType.APPLICATION_JSON));

        for (String ticker : TEN_TICKERS) {
            SecClient.SecCompany company = sec.companyForTicker(ticker.toLowerCase());
            assertThat(company).isNotNull();
            assertThat(company.ticker()).isEqualTo(ticker);
            assertThat(company.name()).isEqualTo(ticker + " Inc.");
        }
        assertThat(sec.companyForTicker("NVDA").cik()).isEqualTo("0000001000"); // padded to ten digits
        assertThat(sec.companyForTicker("NOTLISTED")).isNull(); // not listed: still no second download

        server.verify();
    }

    @Test
    void tenTickersLookedUpAtOnceStillMakeOneRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(tickerList(), MediaType.APPLICATION_JSON));

        ExecutorService callers = Executors.newFixedThreadPool(10);
        try {
            List<Future<SecClient.SecCompany>> found = new ArrayList<>();
            for (String ticker : TEN_TICKERS) found.add(callers.submit(() -> sec.companyForTicker(ticker)));
            for (Future<SecClient.SecCompany> company : found) assertThat(company.get()).isNotNull();

            server.verify();
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void theTickerListIsDownloadedAgainOnceItIsMoreThanADayOld() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-30T09:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        SecClient sec = new SecClient(builder.build(), JsonMapper.builder().build(), 10, clock);
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(tickerList(), MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(TICKERS_URL))
                .andRespond(withSuccess(tickerList(), MediaType.APPLICATION_JSON));

        assertThat(sec.companyForTicker("NVDA")).isNotNull();
        now.set(now.get().plus(Duration.ofHours(23)));
        assertThat(sec.companyForTicker("AMD")).isNotNull(); // still the first download
        now.set(now.get().plus(Duration.ofHours(2)));
        assertThat(sec.companyForTicker("TSM")).isNotNull(); // more than a day old: downloaded again

        server.verify();
    }

    /** The SEC's company_tickers.json: numbered rows with cik_str, ticker and title. */
    private static String tickerList() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < TEN_TICKERS.size(); i++) {
            if (i > 0) json.append(',');
            json.append("\"%d\":{\"cik_str\":%d,\"ticker\":\"%s\",\"title\":\"%s Inc.\"}"
                    .formatted(i, 1000 + i, TEN_TICKERS.get(i), TEN_TICKERS.get(i)));
        }
        return json.append('}').toString();
    }
}
