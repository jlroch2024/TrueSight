package com.truesight.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSearchNameTest {

    @Test
    void removesCommaDetailsAndTrailingLegalEndings() {
        assertThat(NewsSearchName.of("Taiwan Semiconductor Manufacturing Company Limited"))
                .isEqualTo("Taiwan Semiconductor Manufacturing");
        assertThat(NewsSearchName.of("Samsung Electronics Co., Ltd.")).isEqualTo("Samsung Electronics");
        // TS-236: a final Holding is dropped too, so this is now the name newspapers print.
        assertThat(NewsSearchName.of("ASML Holding N.V.")).isEqualTo("ASML");
        assertThat(NewsSearchName.of("NVIDIA Corporation")).isEqualTo("NVIDIA");
    }

    @Test
    void searchesTheNamePeopleUseWithoutSlashMarksOrEndingsNewspapersNeverPrint() {
        assertThat(NewsSearchName.of("QUALCOMM INC/DE")).isEqualTo("QUALCOMM");
        assertThat(NewsSearchName.of("ASML HOLDING NV")).isEqualTo("ASML");
        assertThat(NewsSearchName.of("TAIWAN SEMICONDUCTOR MANUFACTURING CO LTD"))
                .isEqualTo("TAIWAN SEMICONDUCTOR MANUFACTURING");
        assertThat(NewsSearchName.of("Sony Group Corp")).isEqualTo("Sony Group");
        assertThat(NewsSearchName.of("Ford Motor Company")).isEqualTo("Ford Motor");
    }

    @Test
    void dropsASlashMarkWithOrWithoutItsClosingSlash() {
        assertThat(NewsSearchName.of("GENERAL MOTORS CO /NEW/")).isEqualTo("GENERAL MOTORS");
        assertThat(NewsSearchName.of("Example Holdings Inc /DE/")).isEqualTo("Example");
        assertThat(NewsSearchName.of("STMICROELECTRONICS NV")).isEqualTo("STMICROELECTRONICS");
        assertThat(NewsSearchName.of("Example Brasil SA")).isEqualTo("Example Brasil");
    }
}
