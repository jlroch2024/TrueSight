package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The second AI: reads each relationship the first one found, with only its evidence sentence, and says whether it
 * <b>agrees</b> or <b>doubts</b>, and why. It doubts when the sentence describes a plan, a past relationship, a
 * possibility, a competitor, or anything else that is not a current supplier or customer.
 *
 * <p>It never sees the report, so a call is cheap: up to {@link #BATCH_SIZE} claims at once, each numbered from 1 and
 * written like "Sony buys from AMD: semi-custom SoC products", followed by its sentence. Each call is recorded in the AI
 * Audit Trail with the purpose {@code SECOND_CHECK}.
 *
 * <p>A second check is advice for the Analyst, so it can never undo the work it follows. If Gemini fails, or its answer
 * cannot be read, the claims of that call get no verdict and stay saved; the analyst-run pass
 * ({@link SecondCheckPassService}) asks about them again. A claim Gemini leaves out of its answer is treated the same.
 * Nothing here changes a relationship's review status, so the graph does not change until an Analyst decides.
 */
@Component
public class SecondCheck {

    private static final Logger log = LoggerFactory.getLogger(SecondCheck.class);

    /** The most claims sent to Gemini at once. */
    static final int BATCH_SIZE = 30;

    /** The longest reason the {@code relationships} table accepts. */
    private static final int MAX_REASON = 500;

    static final String PROMPT = """
            You are double-checking supplier and customer relationships that another AI read out of annual reports. Each \
            numbered claim says that one company buys from, or sells to, another, and is followed by the sentence from the \
            annual report that the claim was taken from.

            For every claim, decide from its sentence alone whether the sentence shows that the two companies currently \
            have that supplier or customer relationship.

            Answer for every claim:
            - index: the claim's number
            - verdict: AGREES if the sentence shows a current supplier or customer relationship; DOUBTS if it does not
            - reason: one sentence explaining your verdict

            Answer DOUBTS when the sentence describes a plan, a past relationship, a possibility, a competitor, or anything \
            else that is not a current supplier or customer.

            Use only the sentence. Do not guess facts it does not state. Answer with JSON only.

            CLAIMS:
            """;

    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final CompanyRepository companies;
    private final AiCallLog calls;

    public SecondCheck(GeminiClient gemini, GeminiAnswer answers, CompanyRepository companies, AiCallLog calls) {
        this.gemini = gemini;
        this.answers = answers;
        this.companies = companies;
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
     * Asks Gemini about these relationships, {@link #BATCH_SIZE} at a time, and returns the verdict for each one it
     * answered, by relationship id. A relationship in a call that failed, or that Gemini left out, has no entry.
     */
    Map<Long, Result> ask(List<Relationship> found, Long reportId) {
        Map<Long, Result> results = new HashMap<>();
        if (found.isEmpty()) {
            return results;
        }
        Map<Long, Company> companyById = companies.findAllById(found.stream()
                        .flatMap(r -> Stream.of(r.getCompanyId(), r.getCounterpartyId()))
                        .distinct().toList())
                .stream().collect(Collectors.toMap(Company::getId, Function.identity()));
        for (int start = 0; start < found.size(); start += BATCH_SIZE) {
            List<Relationship> batch = found.subList(start, Math.min(start + BATCH_SIZE, found.size()));
            results.putAll(askAbout(batch, reportId, companyById));
        }
        return results;
    }

    private Map<Long, Result> askAbout(List<Relationship> batch, Long reportId, Map<Long, Company> companyById) {
        Map<Long, Result> results = new HashMap<>();
        try {
            // The prompt holds claims and sentences, never a report's text, so it is recorded as it is.
            String prompt = prompt(batch, companyById);
            AiAnswer answer = gemini.generateSecondCheck(
                    new AiRequest(AiPurpose.SECOND_CHECK, prompt, reportId, prompt));
            List<GeminiAnswer.SecondCheckFound> read;
            try {
                read = answers.readSecondChecks(answer.text());
            } catch (GeminiAnswer.UnreadableAnswerException exception) {
                calls.unreadable(answer.callId());
                throw exception;
            }
            for (GeminiAnswer.SecondCheckFound item : read) {
                if (item.index() < 1 || item.index() > batch.size()) {
                    continue; // a claim that was never asked
                }
                results.putIfAbsent(batch.get(item.index() - 1).getId(),
                        new Result(item.verdict(), cut(item.reason(), MAX_REASON)));
            }
            calls.finish(answer.callId(), results.size(), batch.size() - results.size());
        } catch (RuntimeException exception) {
            // Gemini failed, or its answer could not be read: this call's claims stay without a second check.
            log.warn("The second check of {} relationship(s) failed; the pass will ask again.", batch.size(), exception);
            results.clear();
        }
        return results;
    }

    /** The claims, numbered from 1, each with the report's own sentence. */
    String prompt(List<Relationship> batch, Map<Long, Company> companyById) {
        StringBuilder text = new StringBuilder(PROMPT);
        for (int i = 0; i < batch.size(); i++) {
            Relationship relationship = batch.get(i);
            text.append(i + 1).append(". Claim: ").append(claim(relationship, companyById)).append('\n')
                    .append("   Sentence: ").append(oneLine(relationship.getEvidence())).append('\n');
        }
        return text.toString();
    }

    /** "Sony buys from AMD: semi-custom SoC products". A SUPPLIER's counterparty supplies the company. */
    static String claim(Relationship relationship, Map<Long, Company> companyById) {
        String company = companyById.get(relationship.getCompanyId()).getName();
        String counterparty = companyById.get(relationship.getCounterpartyId()).getName();
        String claim = relationship.getType() == RelationshipType.SUPPLIER
                ? company + " buys from " + counterparty
                : company + " sells to " + counterparty;
        String provides = relationship.getProvides();
        return provides == null || provides.isBlank() ? claim : claim + ": " + provides.strip();
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
