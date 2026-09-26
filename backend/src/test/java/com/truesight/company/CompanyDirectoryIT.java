package com.truesight.company;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves a company is found however its name is written: by SEC company number, by a short name such as "TSMC",
 * or by ignoring capitals, punctuation and endings such as "Inc." and "Ltd.".
 */
@IntegrationTest
class CompanyDirectoryIT {

    @Autowired
    CompanyDirectory directory;

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
}
