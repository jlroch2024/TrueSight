package com.truesight.relationship;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSectionTest {

    private static final String NVIDIA = Examples.nvidia10K();

    @Test
    void aTenKIsSentFromItem1BusinessUpToItem2PropertiesSkippingTheTableOfContents() {
        String sent = ReportSection.forAi("10-K", NVIDIA);

        // docs/examples/README.md: the section Gemini was sent for NVIDIA was 167,171 characters.
        assertThat(sent).hasSize(167_171)
                .startsWith("Item 1. Business\nOur Company")
                .contains("Item 1A. Risk Factors")
                .contains("We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited, or TSMC")
                .doesNotContain("Item 2. Properties")
                .doesNotContain("Item 5. Market for Registrant");
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
}
