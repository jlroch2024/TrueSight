package com.truesight.company;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that confirms two names may be one company: after dropping legal endings, one name begins with the other's
 * first word. It never joins companies alone; {@link CompanyDirectoryIT} checks it together with the ticker.
 */
class CompanyNameAgreementTest {

    @Test
    void sonyAndSonyGroupAgreeInEitherOrder() {
        assertThat(CompanyNameKeys.namesAgree("Sony", "Sony Group")).isTrue();
        assertThat(CompanyNameKeys.namesAgree("Sony Group", "Sony")).isTrue();
    }

    @Test
    void legalEndingsAndCapitalsAreIgnored() {
        assertThat(CompanyNameKeys.namesAgree("SONY GROUP CORP", "Sony")).isTrue();
        assertThat(CompanyNameKeys.namesAgree("Sony Corporation", "SONY GROUP CORP")).isTrue();
        assertThat(CompanyNameKeys.namesAgree("Sony Group Corporation", "Sony Interactive Entertainment LLC")).isTrue();
    }

    @Test
    void unrelatedNamesDoNotAgree() {
        assertThat(CompanyNameKeys.namesAgree("Apple Inc.", "Applied Materials, Inc.")).isFalse();
        assertThat(CompanyNameKeys.namesAgree("Sony", "Sonic Automotive")).isFalse();
        assertThat(CompanyNameKeys.namesAgree("Advanced Micro Devices", "AMD")).isFalse();
    }

    @Test
    void aWordMustMatchWholeNotJustItsStart() {
        assertThat(CompanyNameKeys.namesAgree("Son", "Sony Group")).isFalse();
    }

    @Test
    void emptyOrMissingNamesAgreeWithNothing() {
        assertThat(CompanyNameKeys.namesAgree("", "Sony")).isFalse();
        assertThat(CompanyNameKeys.namesAgree(null, "Sony")).isFalse();
        assertThat(CompanyNameKeys.namesAgree("Sony", "  ")).isFalse();
    }

    @Test
    void aTickerIsKeptOnlyWhenItLooksLikeOne() {
        assertThat(CompanyDirectory.cleanTicker(" sony ")).isEqualTo("SONY");
        assertThat(CompanyDirectory.cleanTicker("BRK.B")).isEqualTo("BRK.B");
        assertThat(CompanyDirectory.cleanTicker("")).isNull();
        assertThat(CompanyDirectory.cleanTicker(null)).isNull();
        assertThat(CompanyDirectory.cleanTicker("NYSE: SONY")).isNull();
        assertThat(CompanyDirectory.cleanTicker("TOOLONGTICKER")).isNull();
    }
}
