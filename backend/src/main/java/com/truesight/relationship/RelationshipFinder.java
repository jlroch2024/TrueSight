package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.report.Report;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the suppliers and customers an annual report names, with the AI, and saves the ones it can prove.
 *
 * <ol>
 *   <li>Only the business and risk sections are sent ({@link ReportSection}).</li>
 *   <li>Gemini lists each relationship with a quote ({@link GeminiClient}, {@link GeminiAnswer}). If its answer is
 *       not the JSON asked for, it is asked once more.</li>
 *   <li>Each quote is checked against the report ({@link QuoteChecker}). One that is not in the report is dropped;
 *       one that is, is saved with the report's own words as the evidence, never the AI's.</li>
 *   <li>Every supplier and customer goes through {@link CompanyDirectory#findOrCreate}, with the AI's
 *       {@code alsoKnownAs} as another name.</li>
 * </ol>
 *
 * <p>Reports are public, so a report that already has relationships is never sent to the AI again, whichever
 * portfolio asks.
 *
 * <p>Each call is recorded in the AI Audit Trail with the report's text replaced by a marker, and with how many quotes
 * were kept and how many dropped because they were not in the report.
 */
@Service
public class RelationshipFinder {

    /** The longest values the {@code relationships} and {@code companies} tables accept. */
    private static final int MAX_PROVIDES = 500;
    private static final int MAX_NAME = 300;

    private static final String PROMPT = """
            Below is part of %1$s's annual report (%2$s). List every company this text names as a supplier to %1$s \
            or as a customer of %1$s.

            For each one give:
            - company: the company's name, as written in the text
            - alsoKnownAs: a short name the text gives for it, such as an abbreviation, or an empty string
            - relationship: SUPPLIER if it supplies %1$s, CUSTOMER if it buys from %1$s
            - provides: what is supplied, in a few words
            - quote: one sentence copied exactly from the text that shows the relationship
            - criticality: SOLE_SOURCE if the report says this is the only possible supplier, SINGLE_SOURCE if it says
              this is the one supplier used though others exist, or an empty string when it says neither
            - criticalityQuote: one sentence copied exactly from the text that supports criticality, or an empty string
              when criticality is empty

            Never infer sole or single source from words such as "primary", "key" or "significant" alone. Only report
            criticality when the report explicitly says so.

            Only include companies the text names explicitly. Answer with JSON only.

            TEXT:
            %3$s""";

    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final CompanyDirectory companies;
    private final RelationshipRepository relationships;
    private final AiCallLog calls;

    public RelationshipFinder(GeminiClient gemini, GeminiAnswer answers, CompanyDirectory companies,
                              RelationshipRepository relationships, AiCallLog calls) {
        this.gemini = gemini;
        this.answers = answers;
        this.companies = companies;
        this.relationships = relationships;
        this.calls = calls;
    }

    /**
     * Finds and saves the relationships in {@code report}, which is {@code company}'s. Does nothing if the report has
     * been analysed already.
     *
     * @throws GeminiClient.GeminiException if the AI fails; its message is the reason to show on the holding
     */
    // An AI failure must not undo the caller's work: it still saves the report and marks the holding Failed.
    @Transactional(noRollbackFor = GeminiClient.GeminiException.class)
    public void findIn(Report report, Company company) {
        if (relationships.existsByReportId(report.getId())) {
            return;
        }
        String section = ReportSection.forAi(report.getForm(), report.getText());
        AiRequest request = new AiRequest(AiPurpose.FIND_RELATIONSHIPS,
                PROMPT.formatted(company.getName(), report.getForm(), section), report.getId(),
                PROMPT.formatted(company.getName(), report.getForm(), AiRequest.reportMarker(report.getId(), section)));
        Asked asked = ask(request);

        QuoteChecker checker = new QuoteChecker(report.getText());
        Set<String> saved = new HashSet<>();
        List<Relationship> proven = new ArrayList<>();
        int dropped = 0;
        for (GeminiAnswer.Found item : asked.found()) {
            Optional<String> evidence = checker.evidenceFor(item.quote());
            if (evidence.isEmpty()) {
                dropped++;
                continue;
            }
            Company counterparty = companies.findOrCreate(cut(item.company(), MAX_NAME), null, null,
                    List.of(cut(item.alsoKnownAs(), MAX_NAME)));
            if (counterparty.getId().equals(company.getId())
                    || !saved.add(counterparty.getId() + " " + item.relationship() + " " + evidence.get())) {
                continue; // the company itself, or the same relationship twice
            }
            Relationship relationship = new Relationship(report.getId(), company.getId(), counterparty.getId(),
                    item.relationship(), item.provides().isEmpty() ? null : cut(item.provides(), MAX_PROVIDES),
                    evidence.get());
            if (item.criticality() != null) {
                checker.evidenceFor(item.criticalityQuote()).ifPresent(criticalityEvidence -> {
                    relationship.setCriticality(item.criticality());
                    relationship.setCriticalityEvidence(criticalityEvidence);
                });
            }
            proven.add(relationship);
        }
        relationships.saveAll(proven);
        calls.finish(asked.callId(), proven.size(), dropped);
    }

    /** What Gemini found, and the record of the call that found it. */
    private record Asked(List<GeminiAnswer.Found> found, Long callId) {
    }

    /** Asks Gemini, and asks once more if the answer cannot be read. Each unreadable answer is marked so. */
    private Asked ask(AiRequest request) {
        try {
            return read(request);
        } catch (GeminiAnswer.UnreadableAnswerException first) {
            try {
                return read(request);
            } catch (GeminiAnswer.UnreadableAnswerException second) {
                throw new GeminiClient.GeminiException(
                        "The AI's answer could not be read, even when asked a second time.", second);
            }
        }
    }

    private Asked read(AiRequest request) {
        AiAnswer answer = gemini.generate(request);
        try {
            return new Asked(answers.read(answer.text()), answer.callId());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId());
            throw exception;
        }
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
