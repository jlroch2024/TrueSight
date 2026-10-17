package com.truesight.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * The only class that talks to Logo.dev's brand search: given a company's name, it finds the company's website domain
 * ("Carl Zeiss SMT" gives "zeiss.com"), which the graph then uses to load the company's logo. Companies with a ticker
 * never need it; it is for the private companies the reports name. Tests replace it with a fake.
 *
 * <p>The search needs Logo.dev's secret key ({@code LOGO_DEV_SECRET_KEY}), which stays on the server and is never sent
 * to the browser. Without it, nothing is asked and no domain is found, and the graph falls back to asking by name.
 */
@Component
public class LogoDomainClient {

    private static final Logger log = LoggerFactory.getLogger(LogoDomainClient.class);

    private static final String URL = "https://api.logo.dev/search?q={query}";

    /** One brand Logo.dev's search knows: its name and its website domain. */
    record Brand(String name, String domain) {
    }

    private final RestClient client;
    private final String secretKey;

    @Autowired
    public LogoDomainClient(@Value("${truesight.logo-dev.secret-key:}") String secretKey) {
        this(RestClient.builder().requestFactory(requestFactory()), secretKey);
    }

    /** For tests, which pass a builder bound to a fake server. */
    LogoDomainClient(RestClient.Builder builder, String secretKey) {
        this.client = builder.build();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    /**
     * The website domain of the brand Logo.dev ranks first for this name, or empty when there is no secret key, no
     * match, or Logo.dev cannot be reached. A missing logo is never worth an error: the graph shows a letter instead.
     */
    public Optional<String> domainOf(String companyName) {
        if (secretKey.isEmpty() || companyName == null || companyName.isBlank()) return Optional.empty();
        try {
            Brand[] brands = client.get()
                    .uri(URL, companyName.trim())
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(Brand[].class);
            if (brands == null || brands.length == 0) return Optional.empty();
            String domain = brands[0].domain();
            return domain == null || domain.isBlank() ? Optional.empty() : Optional.of(domain.trim());
        } catch (RestClientException e) {
            // The key is never logged: only which company could not be looked up, and why.
            log.warn("Logo.dev could not find a domain for {}: {}", companyName, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
