package com.truesight.report;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The only class that talks to sec.gov. Requests are paced and identify the TrueSight client. */
@Component
public class SecClient {

    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final String SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK%s.json";
    private final RestClient client;
    private final ObjectMapper mapper;
    private final long intervalMillis;
    /** The SEC's whole ticker list is downloaded once a day, however many tickers are asked for. */
    private static final Duration TICKER_LIST_LIFE = Duration.ofDays(1);
    private final Clock clock;
    private final Object tickerListLock = new Object();
    private volatile Map<String, SecCompany> tickers = Map.of();
    private volatile Instant tickersLoadedAt;
    private long nextRequestAt;

    @Autowired
    public SecClient(ObjectMapper mapper,
                     @Value("${truesight.sec.user-agent}") String userAgent,
                     @Value("${truesight.sec.requests-per-second:10}") int requestsPerSecond) {
        this(createClient(userAgent), mapper, requestsPerSecond);
    }

    SecClient(RestClient client, ObjectMapper mapper, int requestsPerSecond) {
        this(client, mapper, requestsPerSecond, Clock.systemUTC());
    }

    SecClient(RestClient client, ObjectMapper mapper, int requestsPerSecond, Clock clock) {
        this.clock = clock;
        this.client = client;
        this.mapper = mapper;
        int allowedRate = Math.min(10, Math.max(1, requestsPerSecond));
        this.intervalMillis = 1100L / allowedRate;
    }

    private static RestClient createClient(String userAgent) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    public SecCompany companyForTicker(String ticker) {
        return tickerList().get(ticker.toUpperCase(Locale.ROOT));
    }

    /**
     * The SEC's ticker list as a map by upper-case ticker, downloaded on first use and again once it is more than a
     * day old. The download goes through {@link #request} like any other, so its pacing and User-Agent are the same.
     * If it cannot be downloaded nothing is kept, so the next ticker asked for tries again.
     */
    private Map<String, SecCompany> tickerList() {
        Instant now = clock.instant();
        if (isFresh(tickersLoadedAt, now)) return tickers;
        synchronized (tickerListLock) {
            if (!isFresh(tickersLoadedAt, now)) {
                Map<String, SecCompany> loaded = new HashMap<>();
                for (JsonNode row : getJson(TICKERS_URL)) {
                    String ticker = row.path("ticker").asText().toUpperCase(Locale.ROOT);
                    String cik = String.format("%010d", row.path("cik_str").asLong());
                    // The first row for a ticker wins, as when the list was searched for each ticker.
                    loaded.putIfAbsent(ticker, new SecCompany(row.path("title").asText(), cik, ticker));
                }
                tickers = Map.copyOf(loaded);
                tickersLoadedAt = now;
            }
            return tickers;
        }
    }

    private static boolean isFresh(Instant loadedAt, Instant now) {
        return loadedAt != null && loadedAt.isAfter(now.minus(TICKER_LIST_LIFE));
    }

    /** Returns the first 10-K or 20-F in the recent filings list, as specified by this story. */
    public SecFiling latestAnnualFiling(String cik) {
        List<SecFiling> found = annualFilings(cik, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Returns up to {@code count} of the company's 10-K and 20-F filings from the recent filings list, newest first
     * (the order the SEC lists them in). One request, paced like every other.
     */
    public List<SecFiling> annualFilings(String cik, int count) {
        JsonNode root = getJson(SUBMISSIONS_URL.formatted(cik));
        JsonNode recent = root.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        List<SecFiling> filings = new ArrayList<>();
        for (int i = 0; i < forms.size() && filings.size() < count; i++) {
            String form = forms.get(i).asText();
            if (!form.equals("10-K") && !form.equals("20-F")) continue;
            String accession = recent.path("accessionNumber").path(i).asText();
            String date = recent.path("filingDate").path(i).asText();
            String document = recent.path("primaryDocument").path(i).asText();
            if (accession.isBlank() || date.isBlank() || document.isBlank()) continue;
            filings.add(new SecFiling(form, accession, LocalDate.parse(date), filingUrl(cik, accession, document)));
        }
        return filings;
    }

    /** Every 8-K in the recent filings list, newest first as the SEC lists them. Amendments (8-K/A) are skipped. */
    public List<SecCurrentReport> recentCurrentReports(String cik) {
        return currentReportsIn(cik, getJson(SUBMISSIONS_URL.formatted(cik)));
    }

    /** The 8-Ks in a submissions response, as {@link #recentCurrentReports} reads them. */
    public static List<SecCurrentReport> currentReportsIn(String cik, JsonNode submissions) {
        JsonNode recent = submissions.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        List<SecCurrentReport> found = new ArrayList<>();
        for (int i = 0; i < forms.size(); i++) {
            if (!forms.get(i).asText().equals("8-K")) continue;
            String accession = recent.path("accessionNumber").path(i).asText();
            String accepted = recent.path("acceptanceDateTime").path(i).asText();
            String document = recent.path("primaryDocument").path(i).asText();
            if (accession.isBlank() || accepted.isBlank() || document.isBlank()) continue;
            List<String> items = Arrays.stream(recent.path("items").path(i).asText().split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
            try {
                found.add(new SecCurrentReport(accession, items, Instant.parse(accepted),
                        filingUrl(cik, accession, document)));
            } catch (DateTimeParseException unreadableTime) {
                // A filing whose acceptance time cannot be read cannot be placed in the last 7 days: it is left out.
            }
        }
        return found;
    }

    private static String filingUrl(String cik, String accession, String document) {
        return "https://www.sec.gov/Archives/edgar/data/%s/%s/%s".formatted(
                Long.parseLong(cik), accession.replace("-", ""), document);
    }

    /**
     * The tagged figures (XBRL) filed beside a report: the text of the file ending {@code _htm.xml} and of the one
     * ending {@code _lab.xml}, found in the filing's folder listing. Empty when the filing has no such pair. Three
     * downloads at most, each paced like every other request.
     *
     * @throws SecAccessException if the SEC cannot be reached or answers with something unreadable
     */
    public Optional<TaggedFigures> taggedFigures(Report report) {
        String folder = report.getUrl().substring(0, report.getUrl().lastIndexOf('/') + 1);
        JsonNode items = getJson(folder + "index.json").path("directory").path("item");
        String instance = null;
        String labels = null;
        for (JsonNode item : items) {
            String name = item.path("name").asText();
            if (name.endsWith("_htm.xml") && instance == null) instance = name;
            if (name.endsWith("_lab.xml") && labels == null) labels = name;
        }
        if (instance == null || labels == null) return Optional.empty();
        return Optional.of(new TaggedFigures(
                request(folder + instance, "The SEC tagged figures could not be downloaded."),
                request(folder + labels, "The SEC tagged figures could not be downloaded.")));
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

    /**
     * Waits for the next request slot, then downloads outside the lock, so one large report never holds up every
     * other SEC request: requests still start at most 10 a second, but their downloads can overlap.
     */
    private String request(String url, String fallback) {
        awaitNextSlot();
        try {
            return client.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw new SecAccessException(failureMessage(exception, fallback), exception);
        }
    }

    /** Holds the lock only while waiting for this request's turn, and books the slot after it. */
    private synchronized void awaitNextSlot() {
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
    }

    /** The text of a filing's tagged-figures file and of the file that names its tags. */
    public record TaggedFigures(String instance, String labels) { }
    public record SecCompany(String name, String cik, String ticker) { }
    public record SecFiling(String form, String accessionNumber, LocalDate filingDate, String url) { }
    /** An 8-K: the items it reports (such as "1.05"), when the SEC accepted it, and the filing's address. */
    public record SecCurrentReport(String accessionNumber, List<String> items, Instant acceptedAt, String url) { }

    public static class SecAccessException extends RuntimeException {
        public SecAccessException(String message, Throwable cause) { super(message, cause); }
    }
}
