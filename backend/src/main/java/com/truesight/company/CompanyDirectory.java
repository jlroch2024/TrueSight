package com.truesight.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The one way to get a company: finds it if TrueSight already knows it, otherwise creates it. Never create a
 * {@link Company} any other way.
 *
 * <p>Used by the annual report story (for each holding's company) and the AI story (for every supplier and customer it
 * finds). Because both go through here, the same company is one row, whoever finds it first.
 *
 * <p><b>The matching here is deliberately simple</b>: the SEC company number, or the same name ignoring capitals and
 * spacing. The Show Each Company Once story replaces {@link #nameKey} with proper tidying (endings such as "Inc.",
 * punctuation), so "Samsung Electronics Co., Ltd." and "Samsung Electronics" become one company. It keeps this class's
 * method as it is, so nothing that calls it has to change.
 */
@Service
public class CompanyDirectory {

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
     * A name tidied for comparison. For now only capitals and spacing are ignored; the Show Each Company Once story
     * makes this smarter.
     */
    static String nameKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
