package com.truesight.report;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
