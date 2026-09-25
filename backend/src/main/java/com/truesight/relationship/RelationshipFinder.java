package com.truesight.relationship;

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

            Only include companies the text names explicitly. Answer with JSON only.

            TEXT:
            %3$s""";

    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final CompanyDirectory companies;
    private final RelationshipRepository relationships;

    public RelationshipFinder(GeminiClient gemini, GeminiAnswer answers, CompanyDirectory companies,
                              RelationshipRepository relationships) {
        this.gemini = gemini;
        this.answers = answers;
        this.companies = companies;
        this.relationships = relationships;
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
        String prompt = PROMPT.formatted(company.getName(), report.getForm(),
                ReportSection.forAi(report.getForm(), report.getText()));
        List<GeminiAnswer.Found> found = ask(prompt);

        QuoteChecker checker = new QuoteChecker(report.getText());
        Set<String> saved = new HashSet<>();
        List<Relationship> proven = new ArrayList<>();
        for (GeminiAnswer.Found item : found) {
            Optional<String> evidence = checker.evidenceFor(item.quote());
            if (evidence.isEmpty()) {
                continue;
            }
            Company counterparty = companies.findOrCreate(cut(item.company(), MAX_NAME), null, null,
                    List.of(cut(item.alsoKnownAs(), MAX_NAME)));
            if (counterparty.getId().equals(company.getId())
                    || !saved.add(counterparty.getId() + " " + item.relationship() + " " + evidence.get())) {
                continue; // the company itself, or the same relationship twice
            }
            proven.add(new Relationship(report.getId(), company.getId(), counterparty.getId(), item.relationship(),
                    item.provides().isEmpty() ? null : cut(item.provides(), MAX_PROVIDES), evidence.get()));
        }
        relationships.saveAll(proven);
    }

    /** Asks Gemini, and asks once more if the answer cannot be read. */
    private List<GeminiAnswer.Found> ask(String prompt) {
        try {
            return answers.read(gemini.generate(prompt));
        } catch (GeminiAnswer.UnreadableAnswerException first) {
            try {
                return answers.read(gemini.generate(prompt));
            } catch (GeminiAnswer.UnreadableAnswerException second) {
                throw new GeminiClient.GeminiException(
                        "The AI's answer could not be read, even when asked a second time.", second);
            }
        }
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
