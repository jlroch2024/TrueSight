package com.truesight.report;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The only class that talks to sec.gov. Requests are paced and identify the TrueSight client. */
@Component
public class SecClient {

    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final String SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK%s.json";
    private final RestClient client;
    private final ObjectMapper mapper;
    private final long intervalMillis;
    private final Map<String, SecCompany> tickerCache = new ConcurrentHashMap<>();
    private long nextRequestAt;

    public SecClient(ObjectMapper mapper,
                     @Value("${truesight.sec.user-agent}") String userAgent,
                     @Value("${truesight.sec.requests-per-second:10}") int requestsPerSecond) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.mapper = mapper;
        int allowedRate = Math.min(10, Math.max(1, requestsPerSecond));
        this.intervalMillis = 1100L / allowedRate;
    }

    public SecCompany companyForTicker(String ticker) {
        String key = ticker.toUpperCase(Locale.ROOT);
        SecCompany cached = tickerCache.get(key);
        if (cached != null) return cached;
        JsonNode all = getJson(TICKERS_URL);
        Iterator<JsonNode> rows = all.iterator();
        while (rows.hasNext()) {
            JsonNode row = rows.next();
            if (key.equals(row.path("ticker").asText().toUpperCase(Locale.ROOT))) {
                String cik = String.format("%010d", row.path("cik_str").asLong());
                SecCompany company = new SecCompany(row.path("title").asText(), cik, ticker.toUpperCase(Locale.ROOT));
                tickerCache.put(key, company);
                return company;
            }
        }
        return null;
    }

    /** Returns the first 10-K or 20-F in the recent filings list, as specified by this story. */
    public SecFiling latestAnnualFiling(String cik) {
        JsonNode root = getJson(SUBMISSIONS_URL.formatted(cik));
        JsonNode recent = root.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        for (int i = 0; i < forms.size(); i++) {
            String form = forms.get(i).asText();
            if (!form.equals("10-K") && !form.equals("20-F")) continue;
            String accession = recent.path("accessionNumber").path(i).asText();
            String date = recent.path("filingDate").path(i).asText();
            String document = recent.path("primaryDocument").path(i).asText();
            if (accession.isBlank() || date.isBlank() || document.isBlank()) continue;
            String url = "https://www.sec.gov/Archives/edgar/data/%s/%s/%s".formatted(
                    Long.parseLong(cik), accession.replace("-", ""), document);
            return new SecFiling(form, accession, LocalDate.parse(date), url);
        }
        return null;
    }

    public String download(String url) {
        return request(url, "The SEC report could not be downloaded.");
    }

    private JsonNode getJson(String url) {
        try {
            String body = request(url, "The SEC could not be reached or returned unreadable data.");
            return mapper.readTree(body == null ? "{}" : body);
        } catch (SecAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SecAccessException("The SEC returned unreadable data.", exception);
        }
    }

    private String failureMessage(Exception exception, String fallback) {
        if (exception instanceof RestClientResponseException response) {
            return "The SEC returned HTTP %d for a request.".formatted(response.getStatusCode().value());
        }
        if (exception instanceof ResourceAccessException) {
            return "The SEC could not be reached. Check the network connection and try again later.";
        }
        return fallback;
    }

    private synchronized String request(String url, String fallback) {
        long now = System.currentTimeMillis();
        long wait = nextRequestAt - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SecAccessException("The SEC request was interrupted.", exception);
            }
        }
        nextRequestAt = System.currentTimeMillis() + intervalMillis;
        try {
            return client.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw new SecAccessException(failureMessage(exception, fallback), exception);
        }
    }

    public record SecCompany(String name, String cik, String ticker) { }
    public record SecFiling(String form, String accessionNumber, LocalDate filingDate, String url) { }

    public static class SecAccessException extends RuntimeException {
        public SecAccessException(String message, Throwable cause) { super(message, cause); }
    }
}
