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

    /** ASML's 20-F puts every printed line in its own div: a sentence must still come out whole, on one line. */
    @Test
    void joinsASentenceBrokenAcrossDivsLikeAsmlsReport() throws Exception {
        String html;
        try (var in = getClass().getResourceAsStream("/reports/asml-20f-zeiss-lines.html")) {
            html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        String text = ReportTextExtractor.extract(html);

        assertThat(text.lines()).anyMatch(line -> line.contains(
                "The number of lithography systems we are able to produce is limited by the production capacity of one"
                        + " of our key suppliers, Carl Zeiss SMT, our sole supplier of lenses, mirrors, illuminators,"
                        + " collectors and other critical optical components (which we refer to as optics)."));
        assertThat(text.lines()).noneMatch(line -> line.endsWith("our key suppliers, Carl Zeiss SMT, our"));
    }

    @Test
    void divsInARowAreOneLineBreakApartNotABlankLine() {
        String html = "<html><body><div>First printed line of</div><div>a sentence.</div><p>A new paragraph.</p></body></html>";

        assertThat(ReportTextExtractor.extract(html)).isEqualTo("First printed line of a sentence.\nA new paragraph.");
    }
}
