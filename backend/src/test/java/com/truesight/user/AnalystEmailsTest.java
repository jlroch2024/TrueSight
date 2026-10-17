package com.truesight.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** How the {@code ANALYST_EMAILS} setting is read: capitals, spaces and empty entries do not matter. */
class AnalystEmailsTest {

    @Test
    void anEmailInTheSettingMatchesWhateverItsCapitalisation() {
        AnalystEmails emails = new AnalystEmails("Analyst@Firm.com");

        assertThat(emails.isAnalyst("analyst@firm.com")).isTrue();
        assertThat(emails.isAnalyst("ANALYST@FIRM.COM")).isTrue();
        assertThat(emails.isAnalyst("Analyst@Firm.com")).isTrue();
    }

    @Test
    void severalEmailsAreCommaSeparatedAndSpacesAroundThemAreIgnored() {
        AnalystEmails emails = new AnalystEmails(" first@firm.com ,SECOND@firm.com,, ");

        assertThat(emails.isAnalyst("first@firm.com")).isTrue();
        assertThat(emails.isAnalyst("second@firm.com")).isTrue();
    }

    @Test
    void anEmailThatIsNotInTheSettingIsNotAnAnalyst() {
        AnalystEmails emails = new AnalystEmails("analyst@firm.com");

        assertThat(emails.isAnalyst("someone-else@firm.com")).isFalse();
        assertThat(emails.isAnalyst("analyst@firm.com.au")).isFalse();
    }

    @Test
    void anEmptySettingMeansNobodyIsAnAnalyst() {
        AnalystEmails emails = new AnalystEmails("");

        assertThat(emails.isAnalyst("")).isFalse();
        assertThat(emails.isAnalyst("analyst@firm.com")).isFalse();
        assertThat(emails.isAnalyst(null)).isFalse();
    }
}
