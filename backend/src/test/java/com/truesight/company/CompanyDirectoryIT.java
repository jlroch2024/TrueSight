package com.truesight.company;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves a company is found however its name is written: by SEC company number, by a short name such as "TSMC",
 * or by ignoring capitals, punctuation and endings such as "Inc." and "Ltd.".
 */
@IntegrationTest
class CompanyDirectoryIT {

    @Autowired
    CompanyDirectory directory;
    @Autowired
    CompanyRepository companies;

    @Test
    void theSameSecNumberIsAlwaysTheSameCompany() {
        Company first = directory.findOrCreate("NVIDIA CORP", "0001045810", "NVDA", List.of());
        Company again = directory.findOrCreate("NVIDIA Corporation", "0001045810", null, List.of());

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void aShortNameGivenOnceFindsTheCompanyLater() {
        Company tsmc = directory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company Limited", null, null, List.of("TSMC"));

        Company byShortName = directory.findOrCreate("TSMC", null, null, List.of());

        assertThat(byShortName.getId()).isEqualTo(tsmc.getId());
    }

    @Test
    void capitalsAndSpacingDoNotMatter() {
        Company first = directory.findOrCreate("Carl Zeiss SMT", null, null, List.of());

        Company again = directory.findOrCreate("  CARL  ZEISS SMT ", null, null, List.of());

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void differentNamesAreDifferentCompanies() {
        Company apple = directory.findOrCreate("Apple Inc.", null, null, List.of());

        Company applied = directory.findOrCreate("Applied Materials, Inc.", null, null, List.of());

        assertThat(applied.getId()).isNotEqualTo(apple.getId());
    }

    @Test
    void tsmcsFullNameShortNameAndNameWithoutLimitedAreOneCompany() {
        Company fullName = directory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company Limited", null, null, List.of("TSMC"));

        Company withoutLimited = directory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company", null, null, List.of());
        Company shortName = directory.findOrCreate("TSMC", null, null, List.of());

        assertThat(withoutLimited.getId()).isEqualTo(fullName.getId());
        assertThat(shortName.getId()).isEqualTo(fullName.getId());
    }

    @Test
    void samsungElectronicsWithAndWithoutItsEndingsAreOneCompany() {
        Company withEndings = directory.findOrCreate("Samsung Electronics Co., Ltd.", null, null, List.of());

        Company withoutEndings = directory.findOrCreate("Samsung Electronics", null, null, List.of());

        assertThat(withoutEndings.getId()).isEqualTo(withEndings.getId());
    }

    @Test
    void aHoldingNamedAsAnotherHoldingsSupplierIsTheSameCompanyAndAppearsOnce() {
        // The holding is analysed first, with its SEC number and ticker.
        Company asHolding = directory.findOrCreate("Broadcom Inc.", "0001730168", "AVGO", List.of());

        // A different holding's report later names it as a supplier, written slightly differently.
        Company asSupplier = directory.findOrCreate("Broadcom", null, null, List.of());

        assertThat(asSupplier.getId()).isEqualTo(asHolding.getId());
        assertThat(asSupplier.getCik()).isEqualTo("0001730168");
        assertThat(asSupplier.getTicker()).isEqualTo("AVGO");
    }

    /** The Sprint 1 audit's real case: holding NVDA and TSM, where NVIDIA's report names TSMC as a supplier. */
    @Test
    void aHoldingNamedByTheSecIsTheSameCompanyAsTheSupplierAReportNames() {
        Company tsmAsHolding = directory.findOrCreate(
                "TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD", "0001046179", "TSM", List.of());

        Company tsmcAsSupplier = directory.findOrCreate(
                "Taiwan Semiconductor Manufacturing Company Limited", null, null, List.of("TSMC"));

        assertThat(tsmcAsSupplier.getId()).isEqualTo(tsmAsHolding.getId());
        assertThat(tsmcAsSupplier.getTicker()).isEqualTo("TSM");
    }

    @Test
    void aSupplierFoundFirstIsTheSameCompanyWhenItIsLaterAnalysedAsAHolding() {
        Company asSupplier = directory.findOrCreate("Lam Research Corporation", null, null, List.of());

        Company asHolding = directory.findOrCreate("LAM RESEARCH CORP", "0000707549", "LRCX", List.of());

        assertThat(asHolding.getId()).isEqualTo(asSupplier.getId());
        assertThat(asHolding.getCik()).isEqualTo("0000707549");
    }

    @Test
    void appleAndAppliedMaterialsStaySeparate() {
        Company apple = directory.findOrCreate("Apple", null, null, List.of());

        Company applied = directory.findOrCreate("Applied Materials", null, null, List.of());

        assertThat(applied.getId()).isNotEqualTo(apple.getId());
    }

    @Test
    void anEndingIsOnlyRemovedFromTheEndOfAName() {
        Company groupDynamics = directory.findOrCreate("Group Dynamics", null, null, List.of());

        Company group = directory.findOrCreate("Group", null, null, List.of());

        assertThat(group.getId()).isNotEqualTo(groupDynamics.getId());
    }

    @Test
    void anNvEndingIsIgnored() {
        Company withEnding = directory.findOrCreate("Koninklijke Philips N.V.", null, null, List.of());

        Company withoutEnding = directory.findOrCreate("Koninklijke Philips", null, null, List.of());

        assertThat(withoutEnding.getId()).isEqualTo(withEnding.getId());
    }

    // ---- the same ticker, when the names also agree (TS-180) -------------------------------------------------------

    /** A word no other test uses, so a name made from it meets no company a test left in the shared database. */
    private static String word() {
        return "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** A made-up SEC number: ten digits, as the column allows. */
    private static String cik() {
        return "9" + java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    private static String ticker() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** The story's own case: the holding "SONY GROUP CORP" from the SEC, and a customer a report calls "Sony". */
    @Test
    void aCustomerWithTheHoldingsTickerAndAnAgreeingNameIsTheHoldingsCompany() {
        String sony = word();
        String ticker = ticker();
        Company holding = directory.findOrCreate(sony.toUpperCase() + " GROUP CORP", cik(), ticker, List.of());

        Company customer = directory.findOrCreate(sony, null, ticker, List.of());

        assertThat(customer.getId()).isEqualTo(holding.getId());
        assertThat(customer.getCik()).isEqualTo(holding.getCik());
    }

    /**
     * The story's own case, with its real names. Nothing the AI said is involved, only how the directory matches a
     * customer "Sony" with ticker SONY to the SEC's holding "SONY GROUP CORP". The companies are removed afterwards.
     */
    @Test
    void aCustomerSonyWithTickerSonyIsTheHoldingSonyGroupCorp() {
        Company holding = directory.findOrCreate("SONY GROUP CORP", "0000313838", "SONY", List.of());
        Company customer = null;
        try {
            customer = directory.findOrCreate("Sony", null, "SONY", List.of());

            assertThat(customer.getId()).isEqualTo(holding.getId());
            assertThat(companies.findById(holding.getId()).orElseThrow().getName()).isEqualTo("SONY GROUP CORP");
            assertThat(companies.findByTickerIgnoreCase("SONY")).extracting(Company::getId)
                    .containsOnly(holding.getId());
        } finally {
            companies.findById(holding.getId()).ifPresent(companies::delete);
            if (customer != null) companies.findById(customer.getId()).ifPresent(companies::delete);
        }
    }

    @Test
    void theSameCompanyIsFoundWhicheverWayRoundTheTwoAreSaved() {
        String sony = word();
        String ticker = ticker();
        String cik = cik();
        Company customerFirst = directory.findOrCreate(sony, null, ticker, List.of());

        Company holdingLater = directory.findOrCreate(sony.toUpperCase() + " GROUP CORP", cik, ticker, List.of());

        assertThat(holdingLater.getId()).isEqualTo(customerFirst.getId());
        assertThat(holdingLater.getCik()).isEqualTo(cik);
    }

    @Test
    void theTickerIsComparedIgnoringCapitals() {
        String name = word();
        String ticker = ticker();
        Company first = directory.findOrCreate(name + " Group", null, ticker, List.of());

        Company again = directory.findOrCreate(name, null, ticker.toLowerCase(), List.of());

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void theAgreeingNameMayBeAnotherNameTheCompanyWasSeenUnder() {
        String ticker = ticker();
        String full = word();
        String shortName = word();
        Company first = directory.findOrCreate(full + " Holdings Limited", null, ticker, List.of(shortName));

        // Not the short name's own tidied form, so only the ticker and the agreeing name can find the company.
        Company again = directory.findOrCreate(shortName + " Industries", null, ticker, List.of());

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void aTickerAloneNeverJoinsTwoCompanies() {
        String ticker = ticker();
        Company existing = directory.findOrCreate(word() + " Inc.", null, ticker, List.of());

        Company unrelated = directory.findOrCreate(word() + " Holdings", null, ticker, List.of());

        assertThat(unrelated.getId()).isNotEqualTo(existing.getId());
    }

    @Test
    void anAgreeingNameAloneNeedsTheSameTickerToJoinTwoCompanies() {
        String name = word();
        Company existing = directory.findOrCreate(name + " Group", null, ticker(), List.of());

        Company otherTicker = directory.findOrCreate(name, null, ticker(), List.of());
        Company noTicker = directory.findOrCreate(name + " Foods", null, null, List.of());

        assertThat(otherTicker.getId()).isNotEqualTo(existing.getId());
        assertThat(noTicker.getId()).isNotEqualTo(existing.getId());
    }

    @Test
    void twoCompaniesWithDifferentSecNumbersAreNeverJoinedByTicker() {
        String name = word();
        String ticker = ticker();
        Company first = directory.findOrCreate(name + " Group", cik(), ticker, List.of());

        Company second = directory.findOrCreate(name, cik(), ticker, List.of());

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void aCompanyWithoutATickerIsGivenOneWhenItsTickerIsLearned() {
        String name = word();
        String ticker = ticker();
        Company known = directory.findOrCreate(name, null, null, List.of());

        Company again = directory.findOrCreate(name, null, ticker, List.of());

        assertThat(again.getId()).isEqualTo(known.getId());
        assertThat(again.getTicker()).isEqualTo(ticker);
    }
}
