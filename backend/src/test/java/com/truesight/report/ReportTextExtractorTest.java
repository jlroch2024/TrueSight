package com.truesight.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTextExtractorTest {

    @Test
    void joinsWrappedLinesWithinAParagraphAndKeepsParagraphsOnSeparateLines() {
        String html = "<html><body><h1>Business</h1><p>First line<br>continues here.</p><p>Second paragraph.</p></body></html>";

        assertThat(ReportTextExtractor.extract(html))
                .isEqualTo("Business\nFirst line continues here.\nSecond paragraph.");
    }
}
