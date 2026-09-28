package com.truesight.company;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link CompanyDirectory#nameKey} tidies names the way the Show Each Company Once story agreed, without a
 * database.
 */
class CompanyDirectoryTest {

    @Test
    void tsmcsNameWithAndWithoutLimitedTidiesToTheSameKey() {
        assertThat(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company Limited"))
                .isEqualTo(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company"));
    }

    /** "TSMC" is a short name, matched by storing it, not by tidying: see {@code CompanyDirectoryIT}. */
    @Test
    void aShortNameDoesNotTidyToTheSameKeyAsTheFullName() {
        assertThat(CompanyDirectory.nameKey("TSMC"))
                .isNotEqualTo(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company Limited"));
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

    /**
     * A holding is named from the SEC's list of companies, which writes names its own way; a report names the same
     * company its own way. These are the SEC's real names, from company_tickers.json.
     */
    @Test
    void theSecsNamesTidyToTheSameKeyAsTheNamesReportsUse() {
        assertThat(CompanyDirectory.nameKey("NVIDIA CORP")).isEqualTo(CompanyDirectory.nameKey("NVIDIA Corporation"));
        assertThat(CompanyDirectory.nameKey("TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD"))
                .isEqualTo(CompanyDirectory.nameKey("Taiwan Semiconductor Manufacturing Company Limited"));
        assertThat(CompanyDirectory.nameKey("LAM RESEARCH CORP")).isEqualTo(CompanyDirectory.nameKey("Lam Research Corporation"));
        assertThat(CompanyDirectory.nameKey("QUALCOMM INC/DE")).isEqualTo(CompanyDirectory.nameKey("QUALCOMM Incorporated"));
        assertThat(CompanyDirectory.nameKey("Sony Group Corp")).isEqualTo(CompanyDirectory.nameKey("Sony Group Corporation"));
        assertThat(CompanyDirectory.nameKey("ASML HOLDING NV")).isEqualTo(CompanyDirectory.nameKey("ASML Holding N.V."));
    }

    @Test
    void theSecsRegistrationMarkAfterASlashIsIgnored() {
        assertThat(CompanyDirectory.nameKey("QUALCOMM INC/DE")).isEqualTo("qualcomm");
        assertThat(CompanyDirectory.nameKey("APPLIED MATERIALS INC /DE")).isEqualTo("applied materials");
        assertThat(CompanyDirectory.nameKey("MARRIOTT INTERNATIONAL INC /MD/")).isEqualTo("marriott international");
    }

    @Test
    void groupAndHoldingAreKeptBecauseTheyCanTellCompaniesApart() {
        assertThat(CompanyDirectory.nameKey("Sony Group Corp")).isEqualTo("sony group");
        assertThat(CompanyDirectory.nameKey("ASML Holding N.V.")).isEqualTo("asml holding");
    }

    @Test
    void stackedEndingsAreEachRemoved() {
        assertThat(CompanyDirectory.nameKey("Samsung Electronics Co., Ltd."))
                .isEqualTo(CompanyDirectory.nameKey("Samsung Electronics Co"));
    }
}
