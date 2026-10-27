package com.truesight.company;

import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies one company's common name and ticker, in one transaction: either it is renamed, or it is joined to the company
 * it duplicates. Never both halves of a join without the other.
 *
 * <p><b>Renaming</b> keeps the old name in {@code company_names}, so matching still finds the company either way.
 *
 * <p><b>Joining</b> happens when the common name is another company's tidied name, or when another company has the same
 * ticker and a name that agrees ({@link CompanyNameKeys#namesAgree}). The company with an SEC number is kept (it is the
 * holding's); otherwise the older one. The duplicate's relationships (as {@code company_id} and as
 * {@code counterparty_id}), names, holdings and reports move to the kept company, and the duplicate is deleted, which
 * also deletes its news: the kept company's news is collected again at the next Check News. A relationship that would
 * link a company to itself, or that is now there twice, is dropped. Two companies that both have SEC numbers are never
 * joined.
 */
@Service
public class CommonNameSaver {

    private final CompanyRepository companies;
    private final CompanyNameRepository names;
    private final CompanyDirectory directory;
    private final RelationshipRepository relationships;

    @PersistenceContext
    private EntityManager entityManager;

    public CommonNameSaver(CompanyRepository companies, CompanyNameRepository names, CompanyDirectory directory,
                           RelationshipRepository relationships) {
        this.companies = companies;
        this.names = names;
        this.directory = directory;
        this.relationships = relationships;
    }

    @Transactional
    public void apply(Long companyId, String commonName, String rawTicker) {
        Company company = companies.findById(companyId).orElse(null);
        if (company == null) {
            return; // joined into another company earlier in this pass
        }
        String common = commonName == null || commonName.isBlank()
                ? company.getName() : cut(commonName.strip(), CommonNameService.MAX_NAME);
        String ticker = CompanyDirectory.cleanTicker(rawTicker);

        Company twin = twinOf(company, common, ticker);
        if (twin != null) {
            if (company.getCik() != null && twin.getCik() != null) {
                return; // two companies that each file with the SEC are never joined
            }
            join(company, twin, common, ticker);
            return;
        }
        rename(company, common);
        if (company.getTicker() == null && ticker != null) {
            company.setTicker(ticker);
        }
        companies.save(company);
    }

    /** The company this one duplicates, if any: by the common name, then by ticker with names that agree. */
    private Company twinOf(Company company, String common, String ticker) {
        Company byName = names.findByNameKey(CompanyDirectory.nameKey(common))
                .filter(known -> !known.getCompanyId().equals(company.getId()))
                .flatMap(known -> companies.findById(known.getCompanyId()))
                .orElse(null);
        if (byName != null) {
            return byName;
        }
        String sameTicker = ticker != null ? ticker : CompanyDirectory.cleanTicker(company.getTicker());
        if (sameTicker == null) {
            return null;
        }
        return companies.findByTickerIgnoreCase(sameTicker).stream()
                .filter(other -> !other.getId().equals(company.getId()))
                .filter(other -> other.getCik() == null || company.getCik() == null
                        || other.getCik().equals(company.getCik()))
                .filter(other -> directory.knownNames(other).anyMatch(known ->
                        CompanyNameKeys.namesAgree(known, common) || CompanyNameKeys.namesAgree(known, company.getName())))
                .min(Comparator.comparing(Company::getId))
                .orElse(null);
    }

    /** Gives the company its common name, and keeps the name it had as another name. */
    private void rename(Company company, String common) {
        String old = company.getName();
        if (common.equals(old)) {
            return;
        }
        company.setName(common);
        directory.remember(company, List.of(old, common));
    }

    private void join(Company company, Company twin, String common, String ticker) {
        Company kept = twin.getCik() != null ? twin
                : company.getCik() != null ? company
                : twin.getId() < company.getId() ? twin : company;
        Company duplicate = kept == twin ? company : twin;
        Long keptId = kept.getId();
        Long duplicateId = duplicate.getId();
        String keptTicker = kept.getTicker() != null ? kept.getTicker()
                : ticker != null ? ticker : duplicate.getTicker();

        entityManager.createQuery("update Relationship r set r.companyId = :kept where r.companyId = :duplicate")
                .setParameter("kept", keptId).setParameter("duplicate", duplicateId).executeUpdate();
        entityManager.createQuery(
                        "update Relationship r set r.counterpartyId = :kept where r.counterpartyId = :duplicate")
                .setParameter("kept", keptId).setParameter("duplicate", duplicateId).executeUpdate();
        entityManager.createQuery("update Holding h set h.companyId = :kept where h.companyId = :duplicate")
                .setParameter("kept", keptId).setParameter("duplicate", duplicateId).executeUpdate();
        entityManager.createQuery("update Report r set r.companyId = :kept where r.companyId = :duplicate")
                .setParameter("kept", keptId).setParameter("duplicate", duplicateId).executeUpdate();
        entityManager.createQuery("update CompanyName n set n.companyId = :kept where n.companyId = :duplicate")
                .setParameter("kept", keptId).setParameter("duplicate", duplicateId).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        dropSelfAndRepeatedRelationships(keptId);
        companies.deleteById(duplicateId); // its news goes with it, and is collected again at the next Check News
        entityManager.flush();

        Company keeper = companies.findById(keptId).orElseThrow();
        if (keeper.getTicker() == null && keptTicker != null) {
            keeper.setTicker(keptTicker);
        }
        if (keeper.getCik() == null) {
            rename(keeper, common); // a company found in a report takes its common name; an SEC name stays
        }
        directory.remember(keeper, List.of(common));
        keeper.setNewsCheckedAt(null);
        companies.save(keeper);
    }

    /** After a join, drops a relationship from a company to itself, and any relationship that is now there twice. */
    private void dropSelfAndRepeatedRelationships(Long keptId) {
        List<Relationship> touching = relationships.findByCompanyIdInOrCounterpartyIdIn(List.of(keptId), List.of(keptId))
                .stream().sorted(Comparator.comparing(Relationship::getId)).toList();
        Set<String> seen = new HashSet<>();
        for (Relationship relationship : touching) {
            String key = relationship.getReportId() + " " + relationship.getCompanyId() + " "
                    + relationship.getCounterpartyId() + " " + relationship.getType() + " " + relationship.getEvidence();
            if (relationship.getCompanyId().equals(relationship.getCounterpartyId()) || !seen.add(key)) {
                relationships.delete(relationship);
            }
        }
        relationships.flush();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
