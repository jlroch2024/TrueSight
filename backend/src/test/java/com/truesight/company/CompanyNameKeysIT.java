package com.truesight.company;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Names saved before TS-87 keep their old tidied form, e.g. "littelfuse inc de" for "LITTELFUSE INC /DE". These tests
 * put a name back into that old form, as a database from before the fix would have it, and check that refreshing
 * brings it up to date, so the name a report uses finds the company again.
 */
@IntegrationTest
class CompanyNameKeysIT {

    @Autowired
    CompanyDirectory directory;

    @Autowired
    CompanyNameRepository names;

    @Autowired
    CompanyNameKeys keys;

    @Test
    void aNameSavedInTheOldTidiedFormIsBroughtUpToDate() {
        Company holding = directory.findOrCreate("LITTELFUSE INC /DE", null, "LFUS", List.of());
        CompanyName saved = names.findByNameKey("littelfuse").orElseThrow();
        saved.setNameKey("littelfuse inc de"); // how the name was tidied before TS-87
        names.saveAndFlush(saved);

        keys.refresh();

        assertThat(names.findById(saved.getId()).orElseThrow().getNameKey()).isEqualTo("littelfuse");
        Company asNamedInAReport = directory.findOrCreate("Littelfuse, Inc.", null, null, List.of());
        assertThat(asNamedInAReport.getId()).isEqualTo(holding.getId());
    }

    @Test
    void anOldFormThatNowRepeatsAnotherNameOfTheSameCompanyIsRemoved() {
        Company devon = directory.findOrCreate("Devon Energy Corporation", null, "DVN", List.of());
        CompanyName old = names.saveAndFlush(new CompanyName(devon.getId(), "DEVON ENERGY CORP/DE", "devon energy corp de"));

        keys.refresh();

        assertThat(names.findById(old.getId())).isEmpty();
        assertThat(names.findByNameKey("devon energy").orElseThrow().getCompanyId()).isEqualTo(devon.getId());
    }

    @Test
    void refreshingAgainChangesNothing() {
        keys.refresh();

        assertThat(keys.refresh()).isZero();
    }
}
