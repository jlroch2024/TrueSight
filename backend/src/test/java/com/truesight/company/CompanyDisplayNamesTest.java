package com.truesight.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link CompanyDisplayNames} spells names the way the Spell Each Company as Its Own Report Does story agreed,
 * without a database: the report lookup is a fake. The only report text used is real, from NVIDIA's 10-K in
 * {@code docs/examples/nvidia-10k.txt}.
 */
class CompanyDisplayNamesTest {

    private ReportRepository reports;
    private CompanyDisplayNames displayNames;

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        when(reports.findLatestIdByCompanyId(any())).thenReturn(Optional.empty());
        displayNames = new CompanyDisplayNames(reports);
    }

    /** The story's table, every row whose company has no report. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            NVIDIA Corporation                                  | NVIDIA
            Systems on Silicon Manufacturing Company Pte. Ltd.  | Systems on Silicon Manufacturing
            Anthropic, PBC                                      | Anthropic
            Carl Zeiss SMT                                      | Carl Zeiss SMT
            MICROSOFT CORP                                      | Microsoft
            """)
    void aCompanyWithNoReportIsSpelledByTheRules(String stored, String shown) {
        assertThat(displayNames.of(company(1L, stored))).isEqualTo(shown);
    }

    /** The website's own name tests, moved here with the rule; with no report, they read as they did. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            NVIDIA CORP                                         | Nvidia
            ADVANCED MICRO DEVICES INC                          | Advanced Micro Devices
            Apple Inc.                                          | Apple
            Taiwan Semiconductor Manufacturing Company Limited  | Taiwan Semiconductor Manufacturing
            """)
    void theWebsitesFormerCasesReadTheSameWithNoReport(String stored, String shown) {
        assertThat(displayNames.of(company(1L, stored))).isEqualTo(shown);
    }

    @Test
    void theSecsRegistrationMarkGoes() {
        assertThat(displayNames.of(company(1L, "QUALCOMM INC/DE"))).isEqualTo("Qualcomm");
    }

    @Test
    void aTrailingComJoinsTheWordBeforeIt() {
        assertThat(displayNames.of(company(1L, "AMAZON COM INC"))).isEqualTo("Amazon.com");
    }

    @Test
    void legalEndingsThenATrailingAmpersandAreDropped() {
        assertThat(displayNames.of(company(1L, "Merck & Co., Inc."))).isEqualTo("Merck");
    }

    @Test
    void aNameIsNeverEmptied() {
        assertThat(displayNames.of(company(1L, "Company"))).isEqualTo("Company");
    }

    @Test
    void shortWordsInCapitalsStayAsTheyAreUsuallyInitials() {
        assertThat(displayNames.of(company(1L, "ASML HOLDING NV"))).isEqualTo("ASML Holding");
    }

    @Test
    void aNameWithNoWordInCapitalsNeverLooksForAReport() {
        displayNames.of(company(1L, "Merck & Co., Inc."));

        verify(reports, never()).findLatestIdByCompanyId(any());
    }

    @Test
    void nvidiaIsSpelledTheWayItsOwn10KSpellsIt() {
        savedReport(1L, 10L, Examples.nvidia10K());

        assertThat(displayNames.of(company(1L, "NVIDIA CORP"))).isEqualTo("NVIDIA");
    }

    /**
     * NVIDIA's 10-K never writes "UnitedHealth", so that word falls to step 6; it writes "Group" 3 times and "group" 8
     * times, and only the 3 count.
     */
    @Test
    void aWordTheReportNeverWritesFallsToTitleCase() {
        savedReport(1L, 10L, Examples.nvidia10K());

        assertThat(displayNames.of(company(1L, "UNITEDHEALTH GROUP INC"))).isEqualTo("Unitedhealth Group");
    }

    /** A real paragraph of NVIDIA's 10-K whose only "nvidia" is its web address, http://www.nvidia.com. */
    @Test
    void aMatchWrittenAllInLowerCaseIsNotCounted() {
        String paragraph = Examples.nvidia10K().lines()
                .filter(line -> line.contains("www.nvidia.com") && !line.contains("NVIDIA"))
                .findFirst().orElseThrow();
        savedReport(1L, 10L, paragraph);

        assertThat(displayNames.of(company(1L, "NVIDIA CORP"))).isEqualTo("Nvidia");
    }

    @Test
    void theReportIsReadOnceForTwoCalls() {
        savedReport(1L, 10L, Examples.nvidia10K());
        Company nvidia = company(1L, "NVIDIA CORP");

        assertThat(displayNames.of(nvidia)).isEqualTo("NVIDIA");
        assertThat(displayNames.of(nvidia)).isEqualTo("NVIDIA");

        verify(reports, times(1)).findById(10L);
    }

    @Test
    void aNewerReportSpellsTheNameAfresh() {
        savedReport(1L, 10L, Examples.nvidia10K());
        Company nvidia = company(1L, "NVIDIA CORP");
        displayNames.of(nvidia);

        savedReport(1L, 11L, Examples.nvidia10K());
        displayNames.of(nvidia);

        verify(reports, times(1)).findById(10L);
        verify(reports, times(1)).findById(11L);
    }

    /** The sample the website's tests use is in the API's exact shape, so its display names follow the same rule. */
    @Test
    void theSampleRelationshipsCarryTheDisplayNamesTheRuleGives() throws Exception {
        JsonNode sample = JsonMapper.builder().build().readTree(Examples.relationships());
        for (JsonNode relationship : sample) {
            for (String side : List.of("company", "counterparty")) {
                JsonNode company = relationship.path(side);
                assertThat(displayNames.of(company(company.path("id").asLong(), company.path("name").asText())))
                        .as(company.path("name").asText())
                        .isEqualTo(company.path("displayName").asText());
            }
        }
    }

    private static Company company(Long id, String name) {
        Company company = new Company(name, null, null);
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    /** Makes {@code text} the company's newest report, as the fake report lookup returns it. */
    private void savedReport(Long companyId, Long reportId, String text) {
        Report report = new Report(companyId, "10-K", "accession-" + reportId, LocalDate.of(2026, 2, 25),
                "https://www.sec.gov/report-" + reportId, text);
        ReflectionTestUtils.setField(report, "id", reportId);
        when(reports.findLatestIdByCompanyId(companyId)).thenReturn(Optional.of(reportId));
        when(reports.findById(reportId)).thenReturn(Optional.of(report));
    }
}
