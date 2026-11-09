package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSectionTest {

    private static final String NVIDIA = Examples.nvidia10K();

    @Test
    void aTenKIsSentFromItem1BusinessUpToItem2PropertiesSkippingTheTableOfContents() {
        String section = ReportSection.section("10-K", NVIDIA);

        // docs/examples/README.md: the section Gemini was sent for NVIDIA was 167,171 characters.
        assertThat(section).hasSize(167_171)
                .startsWith("Item 1. Business\nOur Company")
                .contains("Item 1A. Risk Factors")
                .contains("We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC")
                .doesNotContain("Item 2. Properties")
                .doesNotContain("Item 5. Market for Registrant");
        // TS-229: the section is sent whole, with supplier and customer paragraphs from elsewhere around it.
        String sent = ReportSection.forAi("10-K", NVIDIA);
        assertThat(sent).contains(section).hasSizeLessThanOrEqualTo(ReportSection.MAX_CHARACTERS);
    }

    @Test
    void aTwentyFIsSentFromItem3KeyInformationUpToItem5() {
        String report = String.join("\n",
                "Item 3. Key Information", "Item 4. Information on the Company", "Item 5. Operating and Financial Review",
                "Front matter.",
                "ITEM 3. KEY INFORMATION", "Risk paragraph.",
                "ITEM 4. INFORMATION ON THE COMPANY", "Business paragraph.",
                "ITEM 5. OPERATING AND FINANCIAL REVIEW AND PROSPECTS", "Results paragraph.");

        assertThat(ReportSection.forAi("20-F", report)).isEqualTo(String.join("\n",
                "ITEM 3. KEY INFORMATION", "Risk paragraph.",
                "ITEM 4. INFORMATION ON THE COMPANY", "Business paragraph.", ""));
    }

    @Test
    void theWholeReportIsSentWhenTheHeadingsAreNotFound() {
        String report = "A report with no item headings.\nAnother paragraph.";

        assertThat(ReportSection.forAi("10-K", report)).isEqualTo(report);
        assertThat(ReportSection.forAi("20-F", report)).isEqualTo(report);
    }

    @Test
    void anOverLongSectionKeepsWholeRelationshipParagraphsFirstInTheirOriginalOrder() {
        // NVIDIA's whole report is 340,255 characters, so it has to be cut down to fit.
        String kept = ReportSection.trim(NVIDIA, ReportSection.MAX_CHARACTERS);

        assertThat(kept.length()).isLessThanOrEqualTo(ReportSection.MAX_CHARACTERS);
        List<String> original = Arrays.asList(NVIDIA.split("\n"));
        List<String> keptParagraphs = Arrays.asList(kept.split("\n"));
        assertThat(original).containsSubsequence(keptParagraphs);
        assertThat(original.stream().filter(p -> p.contains("supplier") || p.contains("customer"))
                .filter(p -> p.length() < 5_000))
                .allMatch(keptParagraphs::contains);
        assertThat(kept).contains("We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited");
    }

    @Test
    void relationshipParagraphsComeBeforeOtherSupplyChainParagraphsWhenThereIsNoRoomForBoth() {
        String text = String.join("\n", "Our components ship in quarters.", "Unrelated paragraph.",
                "Our sole supplier is named here.");

        assertThat(ReportSection.trim(text, 40)).isEqualTo("Our sole supplier is named here.");
        assertThat(ReportSection.trim(text, 70))
                .isEqualTo("Our components ship in quarters.\nOur sole supplier is named here.");
    }

    // ---- TS-229: the right part of every report ------------------------------------------------------------------

    @Test
    void everyOneOfTheTwentyNineFilingsGivesItsRecordedSectionAndNoneLessThanFivePercent() {
        JsonNode filings = JsonMapper.builder().build().readTree(Examples.reportSectionFilings());
        assertThat(filings).hasSize(29);
        for (JsonNode filing : filings) {
            String text = rebuilt(filing);
            int[] stretch = ReportSection.stretch(filing.get("form").asString(), text);
            String ticker = filing.get("ticker").asString();
            if (filing.get("start").isString()) {
                assertThat(stretch).as(ticker + " uses the whole report").isNull();
            } else {
                assertThat(stretch).as(ticker + "'s section")
                        .containsExactly(filing.get("start").asInt(), filing.get("end").asInt());
                assertThat(stretch[1] - stretch[0]).as(ticker + "'s share")
                        .isGreaterThanOrEqualTo((int) Math.ceil(ReportSection.MIN_SHARE * text.length()));
            }
        }
    }

    @Test
    void everyChosenSectionStartsAndEndsOnItsRealItemHeadingJudgedByAPlainerCheckThanTheRules() {
        // A check that does not use ReportSection's patterns: the text at each recorded position must read as the
        // heading, with any kind of space made one space. It catches a section that is wrong and recorded as right.
        JsonNode filings = JsonMapper.builder().build().readTree(Examples.reportSectionFilings());
        List<String> whole = new java.util.ArrayList<>();
        for (JsonNode filing : filings) {
            String ticker = filing.get("ticker").asString();
            if (filing.get("start").isString()) {
                whole.add(ticker);
                continue;
            }
            String text = rebuilt(filing);
            boolean tenK = filing.get("form").asString().equals("10-K");
            assertThat(plain(text, filing.get("start").asInt())).as(ticker + " starts on its heading")
                    .matches(tenK ? "^item 1\\W+business.*" : "^item 3(\\W+[a-d])?\\W+key information.*");
            assertThat(plain(text, filing.get("end").asInt())).as(ticker + " ends on its heading")
                    .matches(tenK ? "^item 2\\W+properties.*" : "^item 5\\W+operating and financial review.*");
        }
        // No heading pair at all (Intel's is only in an index), or too short to be the section.
        assertThat(whole).containsExactlyInAnyOrder("HON", "INTC", "RIO", "SHEL", "UL");
    }

    /** The 150 characters at {@code at}, lower case, with every run of spaces and line breaks made one space. */
    private static String plain(String text, int at) {
        return text.substring(at, Math.min(text.length(), at + 150)).replaceAll("[\\s\\u00a0\\u2009]+", " ").toLowerCase()
                .replaceAll("^(item \\d(\\.?[a-d])?) \\.", "$1.");
    }

    @Test
    void aTwentyFEndHeadingThatSaysAndProspectsInMixedCaseIsAHeadingNotAMention() {
        String filler = "Plain words.\n".repeat(60);
        String report = filler + "Item 3. Key Information\n" + filler + "Item 5. Operating and Financial Review and Prospects\nResults.";

        assertThat(ReportSection.section("20-F", report)).startsWith("Item 3. Key Information").doesNotContain("Prospects");
    }

    /** A text of the filing's true length, its item lines at their true places, and line breaks everywhere else. */
    private static String rebuilt(JsonNode filing) {
        char[] text = new char[filing.get("length").asInt()];
        Arrays.fill(text, '\n');
        for (JsonNode line : filing.get("itemLines")) {
            String words = line.get("text").asString();
            words.getChars(0, words.length(), text, line.get("position").asInt());
        }
        return new String(text);
    }

    @Test
    void aHeadingWithANonBreakingSpaceMatches() {
        String text = "Contents\n" + "x\n".repeat(20) + "Item 1. Business\nWe buy from our supplier.\n"
                + "y\n".repeat(20) + "Item 2. Properties\nOffices.";

        assertThat(ReportSection.section("10-K", text)).startsWith("Item 1. Business\n")
                .doesNotContain("Properties");
    }

    @Test
    void applesSectionStartsAtItsRealHeadingWithANonBreakingSpaceNotAtTheContentsPage() {
        String apple = Examples.apple10K();
        int real = apple.indexOf("Item 1.    Business Company Background");
        assertThat(real).isPositive();

        int[] stretch = ReportSection.stretch("10-K", apple);

        assertThat(stretch[0]).isEqualTo(real);
        assertThat(apple.substring(stretch[1])).startsWith("Item 2.    Properties");
        assertThat(ReportSection.forAi("10-K", apple).length()).isGreaterThan(apple.length() / 2);
    }

    @Test
    void nikesSectionRunsFromItsRealItem1ToItsRealItem2PastTheSentenceThatMentionsItem2Properties() {
        String nike = Examples.nike10K();

        String section = ReportSection.section("10-K", nike);

        assertThat(section).startsWith("ITEM 1. BUSINESS GENERAL NIKE, Inc. was incorporated in 1967")
                .contains("Refer to Item 2. Properties for additional information.")
                .contains("Item 1A");
        assertThat(nike.substring(nike.indexOf(section) + section.length())).startsWith("ITEM 2. PROPERTIES");
    }

    @Test
    void intelsIndexOnlyHeadingFallsBackToTheWholeReport() {
        String intel = Examples.intel10K();

        assertThat(ReportSection.stretch("10-K", intel)).isNull();
        String sent = ReportSection.forAi("10-K", intel);
        assertThat(sent.length()).isLessThanOrEqualTo(ReportSection.MAX_CHARACTERS)
                .isGreaterThan((int) (ReportSection.MIN_SHARE * intel.length()));
        assertThat(Arrays.asList(intel.split("\n"))).containsSubsequence(Arrays.asList(sent.split("\n")));
    }

    @Test
    void aMentionOfAHeadingInASentenceIsNotAHeading() {
        String filler = "Plain words.\n".repeat(30);
        String text = filler + "Item 1. Business\nOur business. See Item 2. Properties for offices.\n" + filler
                + "Table of Contents Item 2. Properties At December 31 we had offices.";

        String section = ReportSection.section("10-K", text);

        assertThat(section).startsWith("Item 1. Business").contains("See Item 2. Properties for offices.");
        assertThat(text.substring(text.indexOf(section) + section.length())).startsWith("Item 2. Properties At December");
    }

    @Test
    void aStretchUnderFivePercentOfTheReportIsNotTheSection() {
        String text = "Item 1. Business\nItem 2. Properties\n" + "Plain words.\n".repeat(200);

        assertThat(ReportSection.stretch("10-K", text)).isNull();
        assertThat(ReportSection.forAi("10-K", text)).isEqualTo(text);
    }

    @Test
    void aSupplierOrCustomerParagraphOutsideTheSectionIsSentInTheReportsOrder() {
        String text = String.join("\n", "Our largest customer is Example Retail.", "Plain words.",
                "Item 1. Business", "We make things.", "x".repeat(100), "Item 2. Properties", "Offices.",
                "Example Wafers is our sole supplier of wafers.", "Our components ship in quarters.", "Plain words.");

        assertThat(ReportSection.forAi("10-K", text)).isEqualTo(String.join("\n",
                "Our largest customer is Example Retail.",
                "Item 1. Business", "We make things.", "x".repeat(100),
                "Example Wafers is our sole supplier of wafers.", "Our components ship in quarters."));
    }

    @Test
    void atTheLimitWholeParagraphsAreKeptInTheReportsOrderAndTheTextIsNeverOverTheLimit() {
        String supplierParagraph = "Our supplier is Example Parts. " + "y".repeat(9_000);
        String supplyParagraph = "Our components are bought in. " + "z".repeat(9_000);
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < 10; i++) report.append(supplyParagraph).append('\n');
        report.append("Item 1. Business\n").append("w".repeat(150_000)).append('\n').append("Item 2. Properties\n");
        for (int i = 0; i < 10; i++) report.append(supplierParagraph).append(i).append('\n');
        String text = report.toString();

        String sent = ReportSection.forAi("10-K", text);

        assertThat(sent.length()).isLessThanOrEqualTo(ReportSection.MAX_CHARACTERS);
        List<String> paragraphs = Arrays.asList(sent.split("\n"));
        assertThat(Arrays.asList(text.split("\n"))).containsSubsequence(paragraphs);
        // Supplier paragraphs come before supply-chain ones: all five that fit are supplier paragraphs.
        assertThat(paragraphs).filteredOn(p -> p.startsWith("Our supplier")).hasSize(5);
        assertThat(paragraphs).filteredOn(p -> p.startsWith("Our components")).isEmpty();
    }
}
