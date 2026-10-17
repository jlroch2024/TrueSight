package com.truesight.config;

import com.truesight.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /api/logos/domains?names=Carl Zeiss SMT&names=… gives the website domain of each named company that has one, as
 * {"Carl Zeiss SMT": "zeiss.com"}, so the graph can load logos for companies without a ticker. Names without a known
 * domain are left out; the graph then shows their letter.
 */
@RestController
@RequestMapping("/api/logos")
@Tag(name = "Logos")
public class LogoController {

    /** The most names one request may ask about: more than any one graph has without tickers. */
    static final int MAX_NAMES = 100;

    private final LogoDomainService domains;

    public LogoController(LogoDomainService domains) {
        this.domains = domains;
    }

    @GetMapping("/domains")
    @Operation(summary = "Find the website domains of companies without a ticker, for their logos")
    public Map<String, String> domains(@RequestParam(name = "names", required = false) List<String> names) {
        if (names == null || names.isEmpty()) return Map.of();
        if (names.size() > MAX_NAMES) {
            throw ApiException.badRequest("Ask about " + MAX_NAMES + " companies or fewer at a time.");
        }
        return domains.domainsOf(names);
    }
}
