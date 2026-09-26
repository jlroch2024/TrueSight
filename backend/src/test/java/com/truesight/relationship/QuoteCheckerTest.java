package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every quote here is either copied from NVIDIA's 10-K in {@code docs/examples/}, or such a quote deliberately changed
 * to check it is refused. Each changed quote says what was changed.
 */
class QuoteCheckerTest {

    private static final QuoteChecker NVIDIA = new QuoteChecker(Examples.nvidia10K());

    private static final String TSMC_SENTENCE = "We utilize foundries, such as Taiwan Semiconductor Manufacturing "
            + "Company Limited, or TSMC, and Samsung Electronics Co., Ltd., or Samsung, to produce our semiconductor "
            + "wafers.";

    @Test
    void aQuoteCopiedExactlyPassesAndTheEvidenceIsTheReportsSentence() {
        assertThat(NVIDIA.evidenceFor(TSMC_SENTENCE)).contains(TSMC_SENTENCE);
    }

    @Test
    void theEvidenceIsTheTightestMatchingPassageNotTheSentenceBeforeIt() {
        // In the report this sentence follows "We utilize CoWoS technology for semiconductor packaging.", whose "We"
        // could also line up with the quote's first word.
        String quote = "We engage with independent subcontractors and contract manufacturers such as Hon Hai Precision "
                + "Industry Co., Ltd., Wistron Corporation, and Fabrinet to perform assembly, testing and packaging of "
                + "our final products.";

        assertThat(NVIDIA.evidenceFor(quote)).contains(quote);
    }

    @Test
    void everyQuoteInGeminisRealAnswerIsFoundWordForWord() {
        // docs/examples/README.md: all 7 quotes appear word for word in the report.
        new GeminiAnswer(JsonMapper.builder().build()).read(Examples.geminiNvidia())
                .forEach(found -> assertThat(NVIDIA.evidenceFor(found.quote())).contains(found.quote()));
    }

    @Test
    void capitalsSpacingAndPunctuationAreIgnored() {
        String quote = "we utilize  foundries such as TAIWAN SEMICONDUCTOR MANUFACTURING COMPANY LIMITED or TSMC and "
                + "Samsung Electronics Co Ltd or Samsung to produce our semiconductor wafers";

        assertThat(NVIDIA.evidenceFor(quote)).contains(TSMC_SENTENCE);
    }

    @Test
    void aQuoteWithAFewWordsChangedPassesButTheEvidenceIsTheReportsOwnWording() {
        // The real sentence, with "utilize" changed to "use" and "such as" left out: 20 of its 21 words are found.
        String quote = "We use foundries, Taiwan Semiconductor Manufacturing Company Limited, or TSMC, and Samsung "
                + "Electronics Co., Ltd., or Samsung, to produce our semiconductor wafers.";

        assertThat(NVIDIA.evidenceFor(quote)).contains(TSMC_SENTENCE);
    }

    @Test
    void aQuoteThatIsNotInTheReportIsRefused() {
        // The real sentence with other companies named in it: under 85% of its words are found.
        String quote = "We utilize foundries, such as Intel Corporation, or Intel, and GlobalFoundries Inc., or GF, "
                + "to produce our semiconductor wafers.";

        assertThat(NVIDIA.evidenceFor(quote)).isEmpty();
    }

    @Test
    void aQuoteWithADifferentNumberIsRefused() {
        assertThat(NVIDIA.evidenceFor("In February 2026, the USG granted a license that would allow us to ship small "
                + "amounts of H200 products to specific China-based customers.")).isPresent();

        // The same sentence with 2026 changed to 2025. 22 of its 23 words are found, but not the number.
        assertThat(NVIDIA.evidenceFor("In February 2025, the USG granted a license that would allow us to ship small "
                + "amounts of H200 products to specific China-based customers.")).isEmpty();
    }

    @Test
    void aQuoteWithANegativeWordTheReportDoesNotHaveIsRefused() {
        assertThat(NVIDIA.evidenceFor("We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung."))
                .contains("We purchase memory from SK Hynix Inc., Micron Technology, Inc., and Samsung.");

        // The same sentence with "do not" added. 12 of its 14 words (86%) are found, but not "not".
        assertThat(NVIDIA.evidenceFor("We do not purchase memory from SK Hynix Inc., Micron Technology, Inc., and "
                + "Samsung.")).isEmpty();
    }

    @Test
    void wordsSpreadOverAPassageMuchLongerThanTheQuoteAreRefused() {
        // The first half of one real sentence joined to the second half of another, from different paragraphs.
        String quote = "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC, to "
                + "ship small amounts of H200 products to specific China-based customers.";

        assertThat(NVIDIA.evidenceFor(quote)).isEmpty();
    }

    @Test
    void anEmptyQuoteIsRefused() {
        assertThat(NVIDIA.evidenceFor("  ...  ")).isEmpty();
    }
}
