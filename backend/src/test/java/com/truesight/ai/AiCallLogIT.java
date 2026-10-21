package com.truesight.ai;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI Audit Trail against a real database: every call becomes one row, and it stays even if the caller fails. */
@IntegrationTest
class AiCallLogIT {

    @Autowired AiCallLog log;
    @Autowired AiCallRepository calls;
    @Autowired TransactionTemplate transaction;

    private static final Instant STARTED = Instant.parse("2026-10-21T09:00:00Z");

    private Long recordAnswered() {
        return log.record(AiRequest.of(AiPurpose.JUDGE_NEWS, "1. [NVIDIA] A headline."), "gemini-test-model",
                "{\"assessments\": []}", AiOutcome.ANSWERED, null, STARTED, 1234);
    }

    @Test
    void aCallIsSavedWithItsPurposeModelPromptAnswerOutcomeAndTimeTaken() {
        AiCall call = calls.findById(recordAnswered()).orElseThrow();

        assertThat(call.getPurpose()).isEqualTo(AiPurpose.JUDGE_NEWS);
        assertThat(call.getModel()).isEqualTo("gemini-test-model");
        assertThat(call.getPrompt()).isEqualTo("1. [NVIDIA] A headline.");
        assertThat(call.getResponse()).isEqualTo("{\"assessments\": []}");
        assertThat(call.getOutcome()).isEqualTo(AiOutcome.ANSWERED);
        assertThat(call.getStartedAt()).isEqualTo(STARTED);
        assertThat(call.getDurationMs()).isEqualTo(1234);
        assertThat(call.getKept()).isNull();
    }

    @Test
    void theRecordedPromptIsKeptNotTheOneSentSoAReportsTextIsNeverStored() {
        String reportText = "We utilize foundries, such as Taiwan Semiconductor Manufacturing Company Limited.";
        AiRequest request = new AiRequest(AiPurpose.FIND_RELATIONSHIPS, "TEXT: " + reportText, null,
                "TEXT: " + AiRequest.reportMarker(99L, reportText));

        Long id = log.record(request, "gemini-test-model", null, AiOutcome.FAILED, "The AI could not be reached.",
                STARTED, 10);

        assertThat(calls.findById(id).orElseThrow().getPrompt())
                .isEqualTo("TEXT: [report 99: " + reportText.length() + " characters]")
                .doesNotContain("Taiwan");
    }

    @Test
    void aCallIsSavedWithoutItsReportUntilTheCallerNamesIt() {
        Long id = recordAnswered();
        assertThat(calls.findById(id).orElseThrow().getReportId()).isNull();

        log.attachReport(id, null); // no report: nothing to name
        assertThat(calls.findById(id).orElseThrow().getReportId()).isNull();
    }

    @Test
    void anAnswerThatCouldNotBeReadIsMarkedUnreadable() {
        Long id = recordAnswered();

        log.unreadable(id);

        assertThat(calls.findById(id).orElseThrow().getOutcome()).isEqualTo(AiOutcome.UNREADABLE);
    }

    @Test
    void finishRecordsHowManyQuotesWereKeptAndDropped() {
        Long id = recordAnswered();

        log.finish(id, 5, 2);

        AiCall call = calls.findById(id).orElseThrow();
        assertThat(call.getKept()).isEqualTo(5);
        assertThat(call.getDropped()).isEqualTo(2);
    }

    @Test
    void aCallIsStillRecordedWhenTheCallersWorkIsRolledBack() {
        Long id = transaction.execute(status -> {
            Long recorded = recordAnswered();
            status.setRollbackOnly();
            return recorded;
        });

        assertThat(calls.findById(id)).isPresent();
    }

    @Test
    void anUnknownOrMissingCallIdIsIgnored() {
        log.unreadable(null);
        log.finish(null, 1, 1);
        log.finish(Long.MAX_VALUE, 1, 1);
    }
}
