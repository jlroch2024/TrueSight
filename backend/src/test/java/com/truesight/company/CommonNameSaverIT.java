package com.truesight.company;

import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How one company is renamed, or joined to the company it duplicates, when {@link CommonNameSaver} applies a common
 * name and ticker. The names here are made up and unique to each test, so no company another test left behind can
 * take part; what Gemini answers is not in question, only what the saver does with an answer.
 */
@IntegrationTest
class CommonNameSaverIT {

    @Autowired CommonNameSaver saver;
    @Autowired CompanyDirectory directory;
    @Autowired CompanyRepository companies;
    @Autowired CompanyNameRepository nameRows;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;

    private static String word() {
        return "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String ticker() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static String cik() {
        return "9" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    private Company company(String name) {
        return directory.findOrCreate(name, null, null, List.of());
    }

    private Report reportOf(Company company) {
        return reports.save(new Report(company.getId(), "10-K",
                "sv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), LocalDate.parse("2026-02-04"),
                "https://www.sec.gov/Archives/" + UUID.randomUUID(), "text"));
    }

    private Relationship relationship(Report report, Company from, Company to, String evidence) {
        return relationships.save(new Relationship(report.getId(), from.getId(), to.getId(),
                RelationshipType.SUPPLIER, "parts", evidence));
    }

    // ---- renaming -------------------------------------------------------------------------------------------------

    @Test
    void aCompanyIsRenamedToItsCommonNameAndTheOldNameFindsItStill() {
        String written = word() + " OpCo, LLC";
        String common = word();
        Company stored = company(written);

        saver.apply(stored.getId(), common, "");

        Company renamed = companies.findById(stored.getId()).orElseThrow();
        assertThat(renamed.getName()).isEqualTo(common);
        assertThat(directory.findOrCreate(written, null, null, List.of()).getId()).isEqualTo(stored.getId());
        assertThat(directory.findOrCreate(common, null, null, List.of()).getId()).isEqualTo(stored.getId());
    }

    @Test
    void aCompanyWhoseCommonNameIsItsNameIsUnchanged() {
        String name = word();
        Company stored = company(name);

        saver.apply(stored.getId(), name, "");

        assertThat(companies.findById(stored.getId()).orElseThrow().getName()).isEqualTo(name);
    }

    @Test
    void aBlankCommonNameKeepsTheName() {
        String name = word() + " Ltd";
        Company stored = company(name);

        saver.apply(stored.getId(), "  ", null);

        assertThat(companies.findById(stored.getId()).orElseThrow().getName()).isEqualTo(name);
    }

    @Test
    void aTickerIsSavedWhenTheCompanyHadNoneAndNeverReplacesOne() {
        Company none = company(word());
        Company has = directory.findOrCreate(word(), null, "KEEP", List.of());

        saver.apply(none.getId(), none.getName(), " abcd ");
        saver.apply(has.getId(), has.getName(), "OTHER");

        assertThat(companies.findById(none.getId()).orElseThrow().getTicker()).isEqualTo("ABCD");
        assertThat(companies.findById(has.getId()).orElseThrow().getTicker()).isEqualTo("KEEP");
    }

    @Test
    void aTickerThatIsNotATickerIsIgnored() {
        Company stored = company(word());

        saver.apply(stored.getId(), stored.getName(), "NYSE: SONY");

        assertThat(companies.findById(stored.getId()).orElseThrow().getTicker()).isNull();
    }

    @Test
    void aCompanyThatIsAlreadyGoneIsSkipped() {
        saver.apply(Long.MAX_VALUE, "Anything", "ANY"); // no error: it was joined into another earlier in the pass
    }

    // ---- joining --------------------------------------------------------------------------------------------------

    @Test
    void aCompanyWithTheSecNumberIsKeptAndTheDuplicateMovesIntoIt() {
        String sony = word();
        String ticker = ticker();
        Company holding = directory.findOrCreate(sony.toUpperCase() + " GROUP CORP", cik(), ticker, List.of());
        Company customer = company(sony + " OpCo");
        Company amd = company(word());
        Report report = reportOf(amd);
        Relationship toSony = relationship(report, amd, customer, "Sentence one.");
        Relationship fromSony = relationship(report, customer, amd, "Sentence two.");

        saver.apply(customer.getId(), sony, ticker);

        assertThat(companies.findById(customer.getId())).isEmpty();
        assertThat(relationships.findById(toSony.getId()).orElseThrow().getCounterpartyId()).isEqualTo(holding.getId());
        assertThat(relationships.findById(fromSony.getId()).orElseThrow().getCompanyId()).isEqualTo(holding.getId());
        Company kept = companies.findById(holding.getId()).orElseThrow();
        assertThat(kept.getName()).isEqualTo(sony.toUpperCase() + " GROUP CORP");
        assertThat(directory.findOrCreate(sony + " OpCo", null, null, List.of()).getId()).isEqualTo(holding.getId());
    }

    @Test
    void theOlderCompanyIsKeptWhenNeitherHasASecNumberAndItTakesTheCommonName() {
        String name = word();
        String ticker = ticker();
        Company older = directory.findOrCreate(name + " Group", null, ticker, List.of());
        Company newer = company(name + " Holdings Plc");

        saver.apply(newer.getId(), name, ticker);

        assertThat(companies.findById(newer.getId())).isEmpty();
        Company kept = companies.findById(older.getId()).orElseThrow();
        assertThat(kept.getName()).isEqualTo(name);
        assertThat(directory.findOrCreate(name + " Group", null, null, List.of()).getId()).isEqualTo(older.getId());
        assertThat(directory.findOrCreate(name + " Holdings Plc", null, null, List.of()).getId()).isEqualTo(older.getId());
    }

    @Test
    void aCommonNameThatIsAnotherCompanysNameJoinsThemWithoutATicker() {
        String name = word();
        Company known = company(name);
        Company written = company(name + " Operating Company LLC X");

        saver.apply(written.getId(), name, "");

        assertThat(companies.findById(written.getId())).isEmpty();
        assertThat(companies.findById(known.getId())).isPresent();
    }

    @Test
    void aTickerAloneNeverJoinsTwoCompanies() {
        String ticker = ticker();
        Company first = directory.findOrCreate(word() + " Foods", null, ticker, List.of());
        Company second = company(word() + " Motors");

        saver.apply(second.getId(), second.getName(), ticker);

        assertThat(companies.findById(first.getId())).isPresent();
        assertThat(companies.findById(second.getId())).isPresent();
        assertThat(companies.findById(second.getId()).orElseThrow().getTicker()).isEqualTo(ticker);
    }

    @Test
    void twoCompaniesThatBothHaveSecNumbersAreNeverJoined() {
        String name = word();
        String ticker = ticker();
        Company first = directory.findOrCreate(name + " Group", cik(), ticker, List.of());
        Company second = directory.findOrCreate(name + " Holdings", cik(), "OTHER" + ticker.substring(0, 3), List.of());

        saver.apply(second.getId(), name, ticker);

        assertThat(companies.findById(first.getId())).isPresent();
        assertThat(companies.findById(second.getId())).isPresent();
    }

    @Test
    void aRelationshipBetweenTheTwoCompaniesIsDroppedNotKeptAsALinkToItself() {
        String name = word();
        String ticker = ticker();
        Company kept = directory.findOrCreate(name + " Group", cik(), ticker, List.of());
        Company duplicate = company(name);
        Report report = reportOf(kept);
        Relationship between = relationship(report, kept, duplicate, "A sentence naming both.");

        saver.apply(duplicate.getId(), name, ticker);

        assertThat(relationships.findById(between.getId())).isEmpty();
        assertThat(relationships.findAll()).noneMatch(r -> r.getCompanyId().equals(r.getCounterpartyId()));
    }

    @Test
    void aRelationshipThatIsNowThereTwiceIsKeptOnce() {
        String name = word();
        String ticker = ticker();
        Company kept = directory.findOrCreate(name + " Group", cik(), ticker, List.of());
        Company duplicate = company(name);
        Company amd = company(word());
        Report report = reportOf(amd);
        Relationship first = relationship(report, amd, kept, "The same sentence.");
        Relationship second = relationship(report, amd, duplicate, "The same sentence.");
        Relationship different = relationship(report, amd, duplicate, "Another sentence.");

        saver.apply(duplicate.getId(), name, ticker);

        assertThat(relationships.findById(first.getId())).isPresent();
        assertThat(relationships.findById(second.getId())).isEmpty();
        assertThat(relationships.findById(different.getId()).orElseThrow().getCounterpartyId()).isEqualTo(kept.getId());
    }

    @Test
    void theKeptCompanysNewsIsCollectedAgainAtTheNextCheckNews() {
        String name = word();
        String ticker = ticker();
        Company kept = directory.findOrCreate(name + " Group", cik(), ticker, List.of());
        kept.setNewsCheckedAt(Instant.parse("2026-10-20T10:00:00Z"));
        companies.save(kept);
        Company duplicate = company(name);

        saver.apply(duplicate.getId(), name, ticker);

        assertThat(companies.findById(kept.getId()).orElseThrow().getNewsCheckedAt()).isNull();
    }

    @Test
    void theDuplicatesReportsMoveToTheKeptCompanyToo() {
        String name = word();
        String ticker = ticker();
        Company kept = directory.findOrCreate(name + " Group", cik(), ticker, List.of());
        Company duplicate = company(name);
        Report report = reportOf(duplicate);

        saver.apply(duplicate.getId(), name, ticker);

        assertThat(reports.findById(report.getId()).orElseThrow().getCompanyId()).isEqualTo(kept.getId());
    }
}
