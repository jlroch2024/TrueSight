package com.truesight.relationship;

import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.report.Report;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a report has been read for its suppliers and customers from enough of its text, so it is never asked again.
 *
 * <p>A report counts as read when an answered {@link AiPurpose#FIND_RELATIONSHIPS} call was given at least half as
 * much text as {@link ReportSection#forAi} gives today. The recorded prompt's marker says how much
 * ({@code [report 12: 107 characters]}). A call with no marker counts as not read. A report read from too little text
 * (a contents page) is read again, once: the new call is given today's text, so it then counts as read.
 *
 * <p>A report with relationships and no recorded call at all was read before calls were recorded, and is left as it is.
 */
@Component
public class ReportReading {

    private static final Pattern MARKER = Pattern.compile("\\[report (\\d+): (\\d+) characters]");

    private final AiCallRepository aiCalls;
    private final RelationshipRepository relationships;

    public ReportReading(AiCallRepository aiCalls, RelationshipRepository relationships) {
        this.aiCalls = aiCalls;
        this.relationships = relationships;
    }

    public boolean readFromItsFullText(Report report) {
        var calls = aiCalls.findByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
                report.getId(), AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED);
        if (calls.isEmpty()) {
            return !aiCalls.existsByReportIdAndPurpose(report.getId(), AiPurpose.FIND_RELATIONSHIPS)
                    && relationships.existsByReportId(report.getId());
        }
        long today = ReportSection.forAi(report.getForm(), report.getText()).length();
        return calls.stream().anyMatch(call -> charactersSent(call, report.getId()) * 2 >= today);
    }

    /** How many characters of this report the call was given, from its marker; -1 when it has none. */
    private static long charactersSent(AiCall call, Long reportId) {
        if (call.getPrompt() == null) return -1;
        Matcher marker = MARKER.matcher(call.getPrompt());
        while (marker.find()) {
            if (marker.group(1).equals(String.valueOf(reportId))) return Long.parseLong(marker.group(2));
        }
        return -1;
    }
}
