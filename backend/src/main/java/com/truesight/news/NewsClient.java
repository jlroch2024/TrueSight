package com.truesight.news;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** The only class that calls Google News. */
@Component
public class NewsClient {

    public static final String UNREACHABLE = "Google News could not be reached. Please try again later.";

    private final RestClient client;
    private final GoogleNewsParser parser = new GoogleNewsParser();
    private final String baseUrl;
    private final long intervalMillis;
    private long nextRequestAt;

    public NewsClient(@Value("${truesight.news.base-url:https://news.google.com/rss/search}") String baseUrl,
                      @Value("${truesight.news.requests-per-second:1}") int requestsPerSecond,
                      @Value("${truesight.sec.user-agent}") String userAgent) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.baseUrl = baseUrl;
        this.intervalMillis = 1000L / Math.min(1, Math.max(1, requestsPerSecond));
    }

    public List<NewsItem> search(String companyName) {
        String encodedName = URLEncoder.encode(companyName, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "?q=%22" + encodedName + "%22+when:7d&hl=en-US&gl=US&ceid=US:en");
        try {
            String xml = request(uri);
            return parser.parse(xml == null ? "" : xml);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new NewsAccessException(UNREACHABLE, exception);
        }
    }

    private synchronized String request(URI uri) {
        long wait = nextRequestAt - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new NewsAccessException(UNREACHABLE, exception);
            }
        }
        nextRequestAt = System.currentTimeMillis() + intervalMillis;
        return client.get().uri(uri).retrieve().body(String.class);
    }

    public static class NewsAccessException extends RuntimeException {
        public NewsAccessException(String message) {
            super(message);
        }

        public NewsAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
