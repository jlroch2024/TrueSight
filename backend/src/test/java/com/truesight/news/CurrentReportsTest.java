package com.truesight.news;

import com.truesight.report.SecClient;
import com.truesight.report.SecClient.SecCurrentReport;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which 8-Ks are read, and the headline each becomes, from Clorox's and AITX's real filings in docs/examples/. */
class CurrentReportsTest {

    private static final String CLOROX = "The Clorox Company";
    private static final String AITX = "Artificial Intelligence Technology Solutions Inc.";
    // Clorox's 8-K of 14 August 2023, accession 0001206774-23-000969: Item 8.01, its IT systems attacked.
    private static final SecCurrentReport CLOROX_CYBER_ATTACK = filing("0001206774-23-000969", "8.01,9.01",
            "2023-08-14T21:29:29.000Z",
            "https://www.sec.gov/Archives/edgar/data/21076/000120677423000969/clx4231381-8k.htm");
    // Clorox's 8-K of 22 January 2026, accession 0001206774-26-000048: Items 7.01 and 8.01, buying GOJO.
    private static final SecCurrentReport CLOROX_GOJO_PURCHASE = filing("0001206774-26-000048", "7.01,8.01,9.01",
            "2026-01-22T21:11:47.000Z",
            "https://www.sec.gov/Archives/edgar/data/21076/000120677426000048/clx4585201-8k.htm");
    // AITX's 8-K of 30 October 2026, accession 0001062993-26-005615: Item 8.01, only a press release attached.
    private static final SecCurrentReport AITX_PRESS_RELEASE = filing("0001062993-26-005615", "8.01,9.01",
            "2026-10-30T10:01:48.000Z",
            "https://www.sec.gov/Archives/edgar/data/1498148/000106299326005615/form8k.htm");

    @Test
    void onlyEightKsReportingOneOfTheNineItemsAreKept() {
        List<SecCurrentReport> all = SecClient.currentReportsIn("0000021076",
                JsonMapper.builder().build().readTree(Examples.secSubmissionsClorox()));

        List<SecCurrentReport> kept = CurrentReports.kept(all);

        assertThat(kept).isNotEmpty().allSatisfy(filing ->
                assertThat(filing.items()).containsAnyElementsOf(CurrentReports.ITEMS.keySet()));
        assertThat(all).filteredOn(filing -> filing.accessionNumber().equals("0001206774-26-000586"))
                .singleElement()
                .satisfies(filing -> assertThat(filing.items()).containsExactly("5.02", "7.01", "9.01"));
        assertThat(kept).extracting(SecCurrentReport::accessionNumber)
                .contains("0001206774-23-000969", "0001206774-25-000171")
                .doesNotContain("0001206774-26-000586", "0000021076-26-000028");
        assertThat(CurrentReports.ITEMS).containsOnlyKeys(
                "1.02", "1.03", "1.05", "2.04", "2.05", "2.06", "3.01", "4.02", "8.01");
    }

    @Test
    void theHeadlineIsTheCompanyTheItemAndTheOpeningOfTheItemsOwnText() {
        NewsItem item = CurrentReports.newsItem(CLOROX, CLOROX_CYBER_ATTACK, Examples.sec8kClorox());

        assertThat(item.title()).startsWith("The Clorox Company, Item 8.01: The Clorox Company (the “Company” or "
                + "“Clorox”) has identified unauthorized activity on some of its Information Technology (IT) systems.");
        assertThat(item.title()).doesNotContain("Other Events").endsWith("…");
        String opening = item.title().substring("The Clorox Company, Item 8.01: ".length());
        assertThat(opening.length()).isLessThanOrEqualTo(CurrentReports.OPENING_LIMIT);
        assertThat(item.source()).isEqualTo("SEC 8-K");
        assertThat(item.url()).isEqualTo(CLOROX_CYBER_ATTACK.url());
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2023-08-14T21:29:29Z"));
    }

    @Test
    void anItemWhoseTextOnlyPointsToAnExhibitIsHeadlinedByTheItemsName() {
        NewsItem item = CurrentReports.newsItem(AITX, AITX_PRESS_RELEASE, Examples.sec8kAitx());

        assertThat(item.title()).isEqualTo("Artificial Intelligence Technology Solutions Inc. Files an 8-K: Other Events");
    }

    @Test
    void anEightKThatCouldNotBeDownloadedIsHeadlinedByTheItemsName() {
        NewsItem item = CurrentReports.newsItem(CLOROX, CLOROX_CYBER_ATTACK, null);

        assertThat(item.title()).isEqualTo("The Clorox Company Files an 8-K: Other Events");
        assertThat(item.url()).isEqualTo(CLOROX_CYBER_ATTACK.url());
    }

    @Test
    void theHeadlineStartsAtTheItemsOwnHeadingNotWhereAnotherItemMentionsIt() {
        // Its Item 7.01 begins "As described in Item 8.01 below", before Item 8.01's own heading.
        NewsItem item = CurrentReports.newsItem(CLOROX, CLOROX_GOJO_PURCHASE, Examples.sec8kCloroxGojo());

        assertThat(item.title()).startsWith("The Clorox Company, Item 8.01: On January 22, 2026, Clorox announced "
                + "that it had entered into a membership interest purchase agreement");
    }

    private static SecCurrentReport filing(String accession, String items, String accepted, String url) {
        return new SecCurrentReport(accession, List.of(items.split(",")), Instant.parse(accepted), url);
    }
}
