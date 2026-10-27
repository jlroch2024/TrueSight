package com.truesight.company;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.relationship.GeminiAnswer;
import com.truesight.relationship.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one-off pass that gives the companies already found in reports their common names.
 *
 * <p>A company already analysed is never sent to Gemini again, so companies saved before TS-180 are still named as
 * their reports wrote them ("OpenAI OpCo, LLC"), and may be there twice ("Sony" next to the holding "SONY GROUP
 * CORP"). An Analyst starts this pass. It sends the name of every company without an SEC number, which is every
 * company found in a report, to Gemini in batches of {@value #BATCH_SIZE} (purpose {@code COMMON_NAMES}), asking for
 * each one's common name and ticker. {@link CommonNameSaver} then renames each company whose common name differs, and
 * joins it to the company it duplicates, by the same rule {@link CompanyDirectory#findOrCreate} uses.
 *
 * <p>The common names and tickers come from Gemini's knowledge, not from a report, so they cannot be quote-checked; an
 * Analyst can correct one later (TS-185 Fix or Add a Relationship). A name Gemini does not answer, or a batch that
 * fails or cannot be read, is left as it is: nothing is lost, and running the pass again asks again. Running it a
 * second time after a good first run changes nothing, because every company already has its common name.
 */
@Service
public class CommonNameService {

    private static final Logger log = LoggerFactory.getLogger(CommonNameService.class);

    /** The most names sent to Gemini at once. */
    static final int BATCH_SIZE = 50;

    /** The longest name the {@code companies} table accepts. */
    static final int MAX_NAME = 300;

    static final String PROMPT = """
            Below is a numbered list of company names, each written as it appears in a company's annual report. For \
            each name, say which company it is and how people know it.

            For each one give:
            - name: the name exactly as listed
            - commonName: the name this company is commonly known by, from your own knowledge; this company's own \
            name, never its parent's, a sister company's or another company's in its group; the name as listed if you \
            are not sure
            - ticker: its main stock ticker if it is listed, from your own knowledge, or an empty string

            Answer for every name. Answer with JSON only.

            NAMES:
            """;

    private final CompanyRepository companies;
    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final AiCallLog calls;
    private final CommonNameSaver saver;

    public CommonNameService(CompanyRepository companies, GeminiClient gemini, GeminiAnswer answers, AiCallLog calls,
                             CommonNameSaver saver) {
        this.companies = companies;
        this.gemini = gemini;
        this.answers = answers;
        this.calls = calls;
        this.saver = saver;
    }

    /** Runs the whole pass: every company without an SEC number, {@value #BATCH_SIZE} names at a time. */
    public void run() {
        List<Company> found = companies.findByCikIsNullOrderByIdAsc();
        for (int start = 0; start < found.size(); start += BATCH_SIZE) {
            List<Company> batch = found.subList(start, Math.min(start + BATCH_SIZE, found.size()));
            try {
                check(batch);
            } catch (RuntimeException exception) {
                // One batch failing must not stop the others. Its companies keep their names; the pass can be run again.
                log.warn("The common-names pass failed for a batch of {} companies.", batch.size(), exception);
            }
        }
    }

    /** One Gemini call for one batch, then the renames and joins it answers. */
    private void check(List<Company> batch) {
        String prompt = prompt(batch);
        List<GeminiAnswer.CommonNameFound> read;
        Long callId;
        try {
            AiAnswer answer = gemini.generateCommonNames(new AiRequest(AiPurpose.COMMON_NAMES, prompt, null, prompt));
            callId = answer.callId();
            read = readOrNull(answer);
            if (read == null) {
                // Not the JSON list that was asked for: ask once more, as every other AI call does.
                answer = gemini.generateCommonNames(new AiRequest(AiPurpose.COMMON_NAMES, prompt, null, prompt));
                callId = answer.callId();
                read = readOrNull(answer);
            }
        } catch (GeminiClient.GeminiException exception) {
            log.warn("The AI could not be asked for common names: {}", exception.getMessage());
            return;
        }
        if (read == null) {
            log.warn("The AI's answer about {} company names could not be read, even when asked a second time.",
                    batch.size());
            return;
        }

        Map<String, GeminiAnswer.CommonNameFound> byName = new HashMap<>();
        for (GeminiAnswer.CommonNameFound item : read) {
            byName.putIfAbsent(key(item.name()), item);
        }
        int answered = 0;
        for (Company company : batch) {
            GeminiAnswer.CommonNameFound item = byName.get(key(company.getName()));
            if (item == null) {
                continue; // not answered: left as it is
            }
            answered++;
            try {
                saver.apply(company.getId(), item.commonName(), item.ticker());
            } catch (RuntimeException exception) {
                log.warn("Company {} could not be renamed or joined.", company.getId(), exception);
            }
        }
        calls.finish(callId, answered, batch.size() - answered);
    }

    /** The answer's names, or null when it is not a readable list (marked unreadable in the audit trail). */
    private List<GeminiAnswer.CommonNameFound> readOrNull(AiAnswer answer) {
        try {
            return answers.readCommonNames(answer.text());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId());
            return null;
        }
    }

    /** The names, numbered from 1, one to a line. */
    static String prompt(List<Company> batch) {
        StringBuilder text = new StringBuilder(PROMPT);
        for (int i = 0; i < batch.size(); i++) {
            text.append(i + 1).append(". ").append(batch.get(i).getName()).append('\n');
        }
        return text.toString();
    }

    /** A name compared loosely, so a name Gemini echoes with different spacing or capitals is still found. */
    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
