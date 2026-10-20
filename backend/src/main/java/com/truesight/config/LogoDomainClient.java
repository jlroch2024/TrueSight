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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

    /** Endings that say what kind of company it is, not which: dropped before the second search. */
    private static final Set<String> LEGAL_ENDINGS = Set.of(
            "inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited", "plc", "nv", "bv", "sa",
            "sas", "spa", "ag", "kg", "gmbh", "se", "llc", "lp", "pte", "pbc");

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
     * The website domain of the company with this name, or empty when there is no secret key, no match, or Logo.dev
     * cannot be reached. A missing logo is never worth an error: the graph shows a letter instead.
     *
     * <p>Logo.dev's first brand for the full name is usually right. When it finds nothing, or answers with the name run
     * together ("carlzeisssmt.com" for "Carl Zeiss SMT", a site that has no logo), the name is tried again without its
     * unit words and legal endings ("Carl Zeiss"). The second answer is kept when there is one. A one-word company's
     * site is often its name run together ("fabrinet.com"), so that alone never causes a second search.
     */
    public Optional<String> domainOf(String companyName) {
        if (secretKey.isEmpty() || companyName == null || companyName.isBlank()) return Optional.empty();
        String name = companyName.trim();
        Optional<String> first = search(name);
        String shorter = withoutUnitWords(name);
        if (shorter.equals(name)) return first;
        if (first.isPresent() && !isNameRunTogether(first.get(), name)) return first;
        Optional<String> second = search(shorter);
        return second.isPresent() ? second : first;
    }

    /** The first brand's domain Logo.dev's search gives for this name, or empty. */
    private Optional<String> search(String query) {
        try {
            Brand[] brands = client.get()
                    .uri(URL, query)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(Brand[].class);
            if (brands == null || brands.length == 0) return Optional.empty();
            String domain = brands[0].domain();
            return domain == null || domain.isBlank() ? Optional.empty() : Optional.of(domain.trim());
        } catch (RestClientException e) {
            // The key is never logged: only which company could not be looked up, and why.
            log.warn("Logo.dev could not find a domain for {}: {}", query, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * The name without its trailing legal endings ("Co., Ltd.", "GmbH") and short words in capitals ("SMT"), one word
     * at a time, never emptied: "Carl Zeiss SMT" becomes "Carl Zeiss".
     */
    static String withoutUnitWords(String name) {
        List<String> words = new ArrayList<>(List.of(name.trim().split("\\s+")));
        while (words.size() > 1) {
            String last = words.get(words.size() - 1);
            String plain = last.replaceAll("[^A-Za-z0-9]", "");
            boolean ending = LEGAL_ENDINGS.contains(plain.toLowerCase(Locale.ROOT));
            boolean unit = plain.matches("[A-Z]{2,4}");
            if (!ending && !unit) break;
            words.remove(words.size() - 1);
        }
        return String.join(" ", words).replaceAll("[\\s,]+$", "");
    }

    /** True when the domain is only the name with its spaces and punctuation taken out: "carlzeisssmt.com". */
    static boolean isNameRunTogether(String domain, String name) {
        String site = domain.toLowerCase(Locale.ROOT).split("\\.")[0];
        return site.equals(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
