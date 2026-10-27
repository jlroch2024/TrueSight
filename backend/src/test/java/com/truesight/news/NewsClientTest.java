package com.truesight.news;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewsClientTest {

    @Test
    void concurrentSearchesStillAskGoogleNewsNoMoreThanOncePerSecond() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NewsClient news = new NewsClient(builder.build(), "https://news.test/rss/search", 1);
        List<Long> startedAt = Collections.synchronizedList(new ArrayList<>());
        server.expect(org.springframework.test.web.client.ExpectedCount.times(3),
                        requestTo(startsWith("https://news.test/rss/search")))
                .andExpect(request -> startedAt.add(System.nanoTime()))
                .andRespond(withSuccess("<rss version=\"2.0\"><channel><title>Empty</title></channel></rss>",
                        MediaType.APPLICATION_XML));

        ExecutorService callers = Executors.newFixedThreadPool(3);
        try {
            List<Future<List<NewsItem>>> responses = new ArrayList<>();
            for (int i = 0; i < 3; i++) responses.add(callers.submit(() -> news.search("TSMC")));
            for (Future<List<NewsItem>> response : responses) assertThat(response.get()).isEmpty();

            server.verify();
            List<Long> ordered = startedAt.stream().sorted().toList();
            assertThat(ordered).hasSize(3);
            for (int i = 0; i + 1 < ordered.size(); i++) {
                assertThat(ordered.get(i + 1) - ordered.get(i)).isGreaterThanOrEqualTo(1_000_000_000L);
            }
        } finally {
            callers.shutdownNow();
        }
    }
}
