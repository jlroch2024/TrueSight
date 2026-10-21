package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The real GeminiClient and the real AI Audit Trail together, against a real database and a fake Gemini server.
 *
 * <p>Analysing a holding saves the report and asks Gemini about it in one transaction, so the report is not saved yet
 * when the call is recorded. These tests do the same, and prove the call is recorded, names the report, and never
 * stops the analysis.
 */
@IntegrationTest
class GeminiClientIT {

    @Autowired AiCallLog log;
    @Autowired AiCallRepository calls;
    @Autowired ReportRepository reports;
    @Autowired CompanyDirectory companies;
    @Autowired TransactionTemplate transaction;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    /** Saves a report in the current, unfinished transaction, as analysing a holding does. */
    private Report unsavedReport() {
        Company company = companies.findOrCreate("Audit Test " + UUID.randomUUID(), null, null, List.of());
        String accession = "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return reports.save(new Report(company.getId(), "10-K", accession, LocalDate.parse("2026-02-25"),
                "https://www.sec.gov/", Examples.nvidia10K()));
    }

    private static AiRequest about(Report report) {
        return new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "TEXT: " + report.getText(), report.getId(),
                "TEXT: " + AiRequest.reportMarker(report.getId(), report.getText()));
    }

    @Test
    void anAnswerAboutAReportStillBeingSavedIsRecordedAndNamesTheReport() {
        server.expect(anything())
                .andRespond(withSuccess(Examples.geminiNvidia(), MediaType.APPLICATION_JSON));
        GeminiClient client = new GeminiClient(builder, "test-key", "gemini-test-model", log);

        Long[] ids = transaction.execute(status -> {
            Report report = unsavedReport();
            AiAnswer answer = client.generate(about(report));
            return new Long[] {answer.callId(), report.getId()};
        });

        AiCall call = calls.findById(ids[0]).orElseThrow();
        assertThat(call.getOutcome()).isEqualTo(AiOutcome.ANSWERED);
        assertThat(call.getReportId()).isEqualTo(ids[1]);
        assertThat(call.getPrompt()).startsWith("TEXT: [report " + ids[1] + ": ").doesNotContain("TSMC");
    }

    @Test
    void aFailedCallAboutAReportStillBeingSavedIsRecordedAndTheCallersWorkCarriesOn() {
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        GeminiClient client = new GeminiClient(builder, "test-key", "gemini-test-model", log);
        long before = calls.count();

        Long reportId = transaction.execute(status -> {
            Report report = unsavedReport();
            assertThatThrownBy(() -> client.generate(about(report)))
                    .isInstanceOf(GeminiClient.GeminiException.class);
            return report.getId();
        });

        assertThat(reports.findById(reportId)).isPresent(); // the caller's work was not rolled back
        assertThat(calls.count()).isEqualTo(before + 1);
        assertThat(calls.findTop100ByOrderByStartedAtDesc()).anySatisfy(call -> {
            assertThat(call.getReportId()).isEqualTo(reportId);
            assertThat(call.getOutcome()).isEqualTo(AiOutcome.FAILED);
        });
    }
}
