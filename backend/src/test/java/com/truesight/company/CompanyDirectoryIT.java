package com.truesight.company;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the simple matching every story relies on. The Show Each Company Once story adds its own tests for the
 * smarter matching, such as ignoring "Inc." and "Ltd.".
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
}
