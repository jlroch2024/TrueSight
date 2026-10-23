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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The only class that talks to Logo.dev's brand search: given a company's name, it finds the company's website domain
 * ("Amkor Technology, Inc." gives "amkor.com"), which the graph then uses to load the company's logo. Companies with a
 * ticker never need it; it is for the private companies the reports name. Tests replace it with a fake.
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
            "sas", "spa", "ag", "kg", "gmbh", "se", "llc", "lp", "pte", "pbc", "opco");

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
     * The website domain of the company with this name, or empty when there is no secret key, no brand of that name,
     * or Logo.dev cannot be reached. A missing logo is never worth an error: the graph shows a letter instead.
     *
     * <p>Logo.dev's search is loose, so its first answer is not trusted. The name is searched without its legal endings
     * and unit words, and a brand is taken only when its name is the company's name ("Amkor Technology" for "Amkor
     * Technology, Inc."). Brands Logo.dev makes up from the name ("honhaiprecisionindustry.com"), and brands sharing
     * only some of its words ("Yuan" at a restaurant's site, for King Yuan Electronics; "King" at a games company's),
     * are refused: a letter is better than another company's logo.
     */
    public Optional<String> domainOf(String companyName) {
        if (secretKey.isEmpty() || companyName == null || companyName.isBlank()) return Optional.empty();
        String name = withoutUnitWords(companyName.trim());
        return search(name).stream()
                .filter(brand -> isThisCompany(brand, name))
                .map(brand -> brand.domain().trim())
                .findFirst();
    }

    /** Every brand Logo.dev's search gives for this name, best first, or none. */
    private List<Brand> search(String query) {
        try {
            Brand[] brands = client.get()
                    .uri(URL, query)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(Brand[].class);
            return brands == null ? List.of() : List.of(brands);
        } catch (RestClientException e) {
            // The key is never logged: only which company could not be looked up, and why.
            log.warn("Logo.dev could not find a domain for {}: {}", query, e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** True when the brand is this company, not a made-up entry or another company sharing a word (see domainOf). */
    static boolean isThisCompany(Brand brand, String name) {
        if (brand.name() == null || brand.domain() == null || brand.name().isBlank() || brand.domain().isBlank()) {
            return false;
        }
        // Logo.dev invents an entry from an unknown name: its "name" is the name run together, as a domain.
        if (brand.name().trim().equalsIgnoreCase(brand.domain().trim())) return false;
        return words(withoutUnitWords(brand.name())).equals(words(name));
    }

    /** A name's words, in lower case and without punctuation, for comparing names. */
    private static List<String> words(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("\\s+"))
                .map(word -> word.replaceAll("[^a-z0-9]", ""))
                .filter(word -> !word.isEmpty())
                .toList();
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

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
