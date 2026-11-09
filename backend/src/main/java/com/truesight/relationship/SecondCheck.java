package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyName;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The second AI, a judge of the first. It is told who wrote the report and given the passage around each relationship's
 * evidence, as a human reviewer would be, but <b>not</b> what the first AI claimed: it says what the passage shows
 * ({@link SecondCheckReading}), and code compares that with the claim. So it cannot simply agree with what it is shown,
 * and a reversed claim, a competitor or a plan reads differently from the claim.
 *
 * <p>Each relationship is one call, so no claim's verdict depends on what else is asked with it. Each call is recorded
 * in the AI Audit Trail with the purpose {@code SECOND_CHECK}, and its passage replaced by a marker.
 *
 * <p>The judge's accuracy is measured on claims labelled by hand from real filings: {@code
 * docs/examples/second-check-judge.json}. Changing {@link #PROMPT} means measuring it again; see that file's README entry.
 *
 * <p>A second check is advice for the Analyst, so it can never undo the work it follows. If Gemini fails, or its answer
 * cannot be read, that relationship gets no verdict and stays saved; the analyst-run pass
 * ({@link SecondCheckPassService}) asks about it again. Nothing here changes a relationship's review status, so the
 * graph does not change until an Analyst decides.
 */
@Component
public class SecondCheck {

    private static final Logger log = LoggerFactory.getLogger(SecondCheck.class);

    /** How much of the report around the evidence the judge reads, so a table's heading or the next sentence is there. */
    static final int CHARACTERS_BEFORE = 1000;
    static final int CHARACTERS_AFTER = 500;

    /** The longest reason the {@code relationships} table accepts. */
    private static final int MAX_REASON = 500;

    /** Filled with the writer, the form, the filing date, the other company and the passage. */
    static final String PROMPT = """
            You are reading part of an annual report to find out how two companies are related. Decide only from the \
            passage.

            %1$s wrote this report: its %2$s, filed on %3$s. "We", "our" and "the Company" mean %1$s, and a list or table \
            in the passage that names no other subject is %1$s's own.

            According to this passage, what is %4$s's relationship to %1$s today? Choose one reading:
            - SUPPLIES_THE_WRITER: it sells goods or services to %1$s, makes things for it, or licenses to it.
            - BUYS_FROM_THE_WRITER: it buys goods or services from %1$s, or is its customer.
            - BOTH: the passage states both.
            - COMPETITOR: it is named only as competing with %1$s.
            - ENDED: the passage says the supplying or buying has ended.
            - PLANNED: it is only planned, non-binding, possible or under discussion.
            - OTHER_TIE: a tie with no buying or selling stated, such as owning shares, an acquisition, a joint venture, \
            a lawsuit or a partnership.
            - NOT_STATED: the passage states no relationship between them.

            A report's latest fiscal year, and any agreement it describes as in force, count as today, even when written \
            in the past tense. A company's figures for its latest year show a relationship that is current unless the \
            passage says it has ended.

            reason: one plain sentence, in the passage's own terms, saying what the passage shows.

            Answer with JSON only.

            PASSAGE:
            %5$s""";

    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final CompanyRepository companies;
    private final CompanyNameRepository names;
    private final ReportRepository reports;
    private final AiCallLog calls;

    public SecondCheck(GeminiClient gemini, GeminiAnswer answers, CompanyRepository companies,
                       CompanyNameRepository names, ReportRepository reports, AiCallLog calls) {
        this.gemini = gemini;
        this.answers = answers;
        this.companies = companies;
        this.names = names;
        this.reports = reports;
        this.calls = calls;
    }

    /** One verdict, ready to save. */
    public record Result(SecondCheckVerdict verdict, String reason) { }

    /**
     * Checks these relationships, which are already saved and belong to the report {@code reportId}, and records each
     * verdict on them. Saving them is the caller's job: the entities are changed, not saved. Never throws because the
     * AI failed.
     */
    public void run(List<Relationship> saved, Long reportId) {
        Map<Long, Result> results = ask(saved, reportId);
        for (Relationship relationship : saved) {
            apply(relationship, results.get(relationship.getId()));
        }
    }

    /** Records a verdict on a relationship, and when it was made. Does nothing when there is none. */
    void apply(Relationship relationship, Result result) {
        if (result == null) {
            return;
        }
        relationship.setSecondCheck(result.verdict());
        relationship.setSecondCheckReason(result.reason());
        relationship.setSecondCheckedAt(Instant.now());
    }

    /**
     * Asks Gemini about each of these relationships, and returns the verdict for each one it answered, by relationship
     * id. A relationship whose call failed has no entry.
     */
    Map<Long, Result> ask(List<Relationship> found, Long reportId) {
        Map<Long, Result> results = new HashMap<>();
        Report report = found.isEmpty() ? null : reports.findById(reportId).orElse(null);
        if (report == null) {
            return results;
        }
        String writer = companies.findById(report.getCompanyId()).map(Company::getName).orElseThrow();
        for (Relationship relationship : found) {
            try {
                results.put(relationship.getId(), askAbout(relationship, report, writer));
            } catch (RuntimeException exception) {
                // Gemini failed, or its answer could not be read: this one stays without a second check.
                log.warn("The second check of relationship {} failed; the pass will ask again.",
                        relationship.getId(), exception);
            }
        }
        return results;
    }

    private Result askAbout(Relationship relationship, Report report, String writer) {
        String other = otherCompany(relationship.getCounterpartyId());
        String passage = passage(report.getText(), relationship.getEvidence());
        AiAnswer answer = gemini.generateSecondCheck(new AiRequest(AiPurpose.SECOND_CHECK,
                prompt(writer, report, other, passage), report.getId(),
                prompt(writer, report, other, AiRequest.reportMarker(report.getId(), passage))));
        GeminiAnswer.SecondCheckFound read;
        try {
            read = answers.readSecondCheck(answer.text());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId());
            throw exception;
        }
        calls.finish(answer.callId(), 1, 0);
        return new Result(read.reading().verdictOn(relationship.getType()),
                cut(read.reading().reasonOn(relationship.getType(), read.reason()), MAX_REASON));
    }

    private static String prompt(String writer, Report report, String other, String passage) {
        return PROMPT.formatted(writer, report.getForm(), report.getFilingDate(), other, passage);
    }

    /** The company's name and the others it is known by, as the report may write any of them. */
    private String otherCompany(Long companyId) {
        String name = companies.findById(companyId).map(Company::getName).orElseThrow();
        String others = names.findByCompanyId(companyId).stream()
                .map(CompanyName::getName)
                .filter(other -> !other.equalsIgnoreCase(name))
                .distinct()
                .map(other -> "\"" + other + "\"")
                .collect(Collectors.joining(", "));
        return others.isEmpty() ? name : name + " (also written " + others + ")";
    }

    /**
     * The report's text around the evidence, cut at whole words and on one line. The evidence alone when the report no
     * longer holds it word for word.
     */
    static String passage(String report, String evidence) {
        int start = report.indexOf(evidence);
        if (start < 0) {
            return oneLine(evidence);
        }
        int from = Math.max(0, start - CHARACTERS_BEFORE);
        int to = Math.min(report.length(), start + evidence.length() + CHARACTERS_AFTER);
        while (from > 0 && !Character.isWhitespace(report.charAt(from - 1))) {
            from++;
        }
        while (to < report.length() && !Character.isWhitespace(report.charAt(to))) {
            to--;
        }
        return oneLine(report.substring(from, to));
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
