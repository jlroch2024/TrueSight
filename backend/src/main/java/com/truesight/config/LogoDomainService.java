package com.truesight.config;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the website domains of companies without a ticker, for the graph's logos, and remembers each answer (found or
 * not) for as long as the backend runs, so Logo.dev is asked about each company once. They are kept in memory rather
 * than in the database: a domain is only a nicety for a logo, and Sprint 2 adds no tables.
 */
@Service
public class LogoDomainService {

    private final LogoDomainClient client;
    private final Map<String, Optional<String>> known = new ConcurrentHashMap<>();

    public LogoDomainService(LogoDomainClient client) {
        this.client = client;
    }

    /** Each name that has a domain, with its domain, in the order asked. Names without one are left out. */
    public Map<String, String> domainsOf(List<String> companyNames) {
        Map<String, String> found = new LinkedHashMap<>();
        for (String name : companyNames) {
            if (name == null || name.isBlank()) continue;
            known.computeIfAbsent(name, client::domainOf).ifPresent(domain -> found.put(name, domain));
        }
        return found;
    }
}
