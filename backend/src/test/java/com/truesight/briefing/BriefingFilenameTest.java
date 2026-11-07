package com.truesight.briefing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The name a briefing is saved under, which must be safe in a file name and in a response header. */
class BriefingFilenameTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-06");

    @Test
    void aPortfolioNameBecomesWordsJoinedByHyphensWithTheDateAfterIt() {
        assertThat(BriefingFilename.of("Tech Growth", DAY)).isEqualTo("Tech-Growth-Briefing-2026-11-06.pdf");
    }

    @Test
    void slashesQuotesAndPunctuationNeverReachTheName() {
        assertThat(BriefingFilename.of("Q4 / 2026 \"Core\"", DAY)).isEqualTo("Q4-2026-Core-Briefing-2026-11-06.pdf");
        assertThat(BriefingFilename.of("AI & Semis (US)", DAY)).isEqualTo("AI-Semis-US-Briefing-2026-11-06.pdf");
    }

    @Test
    void aNewLineInANameCannotBreakTheHeader() {
        assertThat(BriefingFilename.of("Line one\r\nX-Injected: yes", DAY))
                .isEqualTo("Line-one-X-Injected-yes-Briefing-2026-11-06.pdf");
    }

    @Test
    void aNameWithNothingUsableIsCalledPortfolio() {
        assertThat(BriefingFilename.of("  ", DAY)).isEqualTo("Portfolio-Briefing-2026-11-06.pdf");
        assertThat(BriefingFilename.of("///", DAY)).isEqualTo("Portfolio-Briefing-2026-11-06.pdf");
        assertThat(BriefingFilename.of(null, DAY)).isEqualTo("Portfolio-Briefing-2026-11-06.pdf");
    }

    @Test
    void aVeryLongNameIsCutAtSixtyCharactersWithoutATrailingHyphen() {
        String name = "A".repeat(59) + " B and more";

        String filename = BriefingFilename.of(name, DAY);

        assertThat(filename).isEqualTo("A".repeat(59) + "-Briefing-2026-11-06.pdf");
    }

    @Test
    void accentsAreDroppedAndANameOfOtherScriptsIsCalledPortfolio() {
        assertThat(BriefingFilename.of("Café Growth", DAY)).isEqualTo("Cafe-Growth-Briefing-2026-11-06.pdf");
        assertThat(BriefingFilename.of("成长", DAY)).isEqualTo("Portfolio-Briefing-2026-11-06.pdf");
    }
}
