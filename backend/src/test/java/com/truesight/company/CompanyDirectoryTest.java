package com.truesight.company;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link CompanyDirectory#nameKey} tidies names the way the Show Each Company Once story agreed, without a
 * database.
 */
class CompanyDirectoryTest {

    @Test
    void tsmcsThreeNamesTidyToTheSameKey() {
        assertThat(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company Limited"))
                .isEqualTo(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company"))
                .isEqualTo(CompanyDirectory.nameKey("TSMC"));
    }

    @Test
    void samsungElectronicsTidiesTheSameWithOrWithoutItsEndings() {
        assertThat(CompanyDirectory.nameKey("Samsung Electronics Co., Ltd."))
                .isEqualTo(CompanyDirectory.nameKey("Samsung Electronics"));
    }

    @Test
    void appleAndAppliedMaterialsTidyToDifferentKeys() {
        assertThat(CompanyDirectory.nameKey("Apple")).isNotEqualTo(CompanyDirectory.nameKey("Applied Materials"));
    }

    @Test
    void capitalsAndExtraSpacingAreIgnored() {
        assertThat(CompanyDirectory.nameKey("  CARL  ZEISS SMT ")).isEqualTo(CompanyDirectory.nameKey("Carl Zeiss SMT"));
    }

    @Test
    void anEndingIsOnlyRemovedFromTheEndOfAName() {
        assertThat(CompanyDirectory.nameKey("Group Dynamics")).isNotEqualTo(CompanyDirectory.nameKey("Group"));
    }

    @Test
    void nvIsIgnoredAsAnEnding() {
        assertThat(CompanyDirectory.nameKey("Koninklijke Philips N.V."))
                .isEqualTo(CompanyDirectory.nameKey("Koninklijke Philips"));
    }

    @Test
    void plcIsIgnoredAsAnEnding() {
        assertThat(CompanyDirectory.nameKey("Diageo plc")).isEqualTo(CompanyDirectory.nameKey("Diageo"));
    }

    @Test
    void corporationIsIgnoredAsAnEnding() {
        assertThat(CompanyDirectory.nameKey("Alphabet Corporation")).isEqualTo(CompanyDirectory.nameKey("Alphabet"));
    }

    @Test
    void stackedEndingsAreEachRemoved() {
        assertThat(CompanyDirectory.nameKey("Samsung Electronics Co., Ltd."))
                .isEqualTo(CompanyDirectory.nameKey("Samsung Electronics Co"));
    }
}
