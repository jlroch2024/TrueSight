package com.truesight.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The one way to get a company: finds it if TrueSight already knows it, otherwise creates it. Never create a
 * {@link Company} any other way.
 *
 * <p>Used by the annual report story (for each holding's company) and the AI story (for every supplier and customer it
 * finds). Because both go through here, the same company is one row, whoever finds it first.
 *
 * <p>Matching is, in order: the SEC company number ({@code cik}); then an exact match on {@link #nameKey the tidied
 * name} of {@code name} or any {@code otherNames}, against every name TrueSight has already stored; otherwise a new
 * company is created. Two different names are only ever the same company when one of these matches: when unsure, they
 * are kept apart, because merging two different companies is worse than showing one company twice.
 */
@Service
public class CompanyDirectory {

    /**
     * Endings ignored when comparing names, because they say nothing about which company it is. Each is a single
     * word once punctuation and capitals are gone, e.g. "N.V." becomes "nv". Only ever removed from the end of a
     * name, one word at a time, so "Group Dynamics" keeps "Group": "Dynamics" is not one of these, so nothing is
     * removed.
     */
    private static final Set<String> IGNORED_ENDINGS =
            Set.of("inc", "ltd", "co", "corporation", "limited", "nv", "plc");

    private final CompanyRepository companies;
    private final CompanyNameRepository names;

    public CompanyDirectory(CompanyRepository companies, CompanyNameRepository names) {
        this.companies = companies;
        this.names = names;
    }

    /**
     * Finds or creates a company, and remembers every name it has been seen under.
     *
     * @param name       the company's name, as written, e.g. "Taiwan Semiconductor Manufacturing Company Limited"
     * @param cik        the SEC company number, or null if it does not file with the SEC
     * @param ticker     its stock ticker, or null if unknown
     * @param otherNames other names for it, such as the AI's {@code alsoKnownAs} ("TSMC"). Empty values are ignored
     */
    @Transactional
    public Company findOrCreate(String name, String cik, String ticker, Collection<String> otherNames) {
        List<String> allNames = new ArrayList<>();
        allNames.add(name);
        otherNames.stream().filter(n -> n != null && !n.isBlank()).forEach(allNames::add);

        Company company = null;
        if (cik != null) {
            company = companies.findByCik(cik).orElse(null);
        }
        for (int i = 0; company == null && i < allNames.size(); i++) {
            company = names.findByNameKey(nameKey(allNames.get(i)))
                    .flatMap(known -> companies.findById(known.getCompanyId()))
                    .orElse(null);
        }
        if (company == null) {
            company = companies.save(new Company(name, cik, ticker));
        } else {
            if (company.getCik() == null && cik != null) {
                company.setCik(cik);
            }
            if (company.getTicker() == null && ticker != null) {
                company.setTicker(ticker);
            }
        }
        remember(company, allNames);
        return company;
    }

    /**
     * A name tidied for comparison: capitals and punctuation are ignored, and an ending such as "Inc.", "Ltd.",
     * "Co.", "Corporation", "Limited" or "N.V." is dropped from the end, one word at a time (so "Co., Ltd." loses
     * both). Two names with the same key are the same company.
     */
    static String nameKey(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return cleaned;
        }

        List<String> words = new ArrayList<>(Arrays.asList(cleaned.split(" ")));
        while (words.size() > 1 && IGNORED_ENDINGS.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        return String.join(" ", words);
    }

    /** Stores each name not seen before, so it is matched straight away next time. */
    private void remember(Company company, List<String> allNames) {
        for (String name : allNames) {
            String key = nameKey(name);
            if (!names.existsByNameKey(key)) {
                names.save(new CompanyName(company.getId(), name.trim(), key));
            }
        }
    }
}
