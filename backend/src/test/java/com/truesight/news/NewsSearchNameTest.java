package com.truesight.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSearchNameTest {

    @Test
    void removesCommaDetailsAndTrailingLegalEndings() {
        assertThat(NewsSearchName.of("Taiwan Semiconductor Manufacturing Company Limited"))
                .isEqualTo("Taiwan Semiconductor Manufacturing");
        assertThat(NewsSearchName.of("Samsung Electronics Co., Ltd.")).isEqualTo("Samsung Electronics");
        assertThat(NewsSearchName.of("ASML Holding N.V.")).isEqualTo("ASML Holding");
        assertThat(NewsSearchName.of("NVIDIA Corporation")).isEqualTo("NVIDIA");
    }
}
