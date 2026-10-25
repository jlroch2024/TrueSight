package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Re-reads stored reports to annotate existing relationships with source criticality, without changing the links. */
@Service
public class CriticalityPassService {

    private static final String PROMPT = """
            Read the business and risk text from %1$s's %2$s annual report. For each listed counterparty, decide only
            whether this report explicitly says it is a sole source (the only possible supplier of something) or a
            single source (the one supplier used, though others exist). Never infer this from words such as "primary",
            "key" or "significant" alone. When the report does not clearly say either, return an empty criticality and
            empty criticalityQuote.

            Return one item for each counterparty listed, using its exact name as company. Include only:
            - company: exact counterparty name from the list
            - criticality: SOLE_SOURCE, SINGLE_SOURCE, or an empty string
            - criticalityQuote: one sentence copied exactly from the report that supports this, or empty when none

            EXISTING RELATIONSHIPS:
            %3$s

            REPORT TEXT:
            %4$s
            """;

    private final ReportRepository reports;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final AiCallLog calls;
    private final AiCallRepository callRows;
    private final CriticalityResultSaver resultSaver;

    public CriticalityPassService(ReportRepository reports, RelationshipRepository relationships,
                                  CompanyRepository companies, GeminiClient gemini, GeminiAnswer answers,
                                  AiCallLog calls, AiCallRepository callRows, CriticalityResultSaver resultSaver) {
        this.reports = reports;
        this.relationships = relationships;
        this.companies = companies;
        this.gemini = gemini;
        this.answers = answers;
        this.calls = calls;
        this.callRows = callRows;
        this.resultSaver = resultSaver;
    }

    /** Reports that have existing relationships still needing a criticality assessment. */
    public List<Long> pendingReportIds() {
        return relationships.findReportIdsWithoutCriticality(RelationshipType.SUPPLIER).stream()
                .filter(reportId -> !callRows.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
                        reportId, AiPurpose.CRITICALITY, AiOutcome.ANSWERED))
                .filter(reportId -> !callRows.existsByReportIdAndPurposeAndOutcomeAndPromptContaining(
                        reportId, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, "criticalityQuote"))
                .distinct().sorted().toList();
    }

    /** Runs one Gemini call for a report, keeping every relationship field except criticality untouched. */
    public void assess(Long reportId) {
        Report report = reports.findById(reportId).orElse(null);
        if (report == null || callRows.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
                reportId, AiPurpose.CRITICALITY, AiOutcome.ANSWERED)
                || callRows.existsByReportIdAndPurposeAndOutcomeAndPromptContaining(
                        reportId, AiPurpose.FIND_RELATIONSHIPS, AiOutcome.ANSWERED, "criticalityQuote")) return;

        List<Relationship> pending = relationships.findByReportIdOrderByIdAsc(reportId).stream()
                .filter(item -> item.getCriticality() == null)
                .filter(item -> item.getType() == RelationshipType.SUPPLIER)
                .toList();
        if (pending.isEmpty()) return;

        Map<Long, Company> counterpartyById = companies.findAllById(
                        pending.stream().map(Relationship::getCounterpartyId).distinct().toList())
                .stream().collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<String, List<Relationship>> byCounterpartyName = new HashMap<>();
        List<String> listed = new ArrayList<>();
        for (Relationship item : pending) {
            Company counterparty = counterpartyById.get(item.getCounterpartyId());
            if (counterparty == null) continue;
            String name = counterparty.getName();
            String key = key(name);
            byCounterpartyName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
            listed.add("- company: %s; relationship: %s; provides: %s".formatted(name, item.getType(),
                    item.getProvides() == null ? "not specified" : item.getProvides()));
        }
        if (listed.isEmpty()) return;

        String reportText = ReportSection.forAi(report.getForm(), report.getText());
        String listedRelationships = String.join("\n", listed);
        String companyName = companies.findById(report.getCompanyId()).map(Company::getName).orElse("this company");
        String prompt = PROMPT.formatted(companyName, report.getForm(), listedRelationships, reportText);
        String recordedPrompt = PROMPT.formatted(companyName, report.getForm(), listedRelationships,
                AiRequest.reportMarker(reportId, reportText));
        AiRequest request = new AiRequest(AiPurpose.CRITICALITY, prompt, reportId, recordedPrompt);
        Asked asked = ask(request);

        QuoteChecker checker = new QuoteChecker(report.getText());
        Map<Long, CriticalityResultSaver.VerifiedCriticality> verified = new HashMap<>();
        int dropped = 0;
        for (GeminiAnswer.CriticalityFound found : asked.found()) {
            List<Relationship> matching = byCounterpartyName.get(key(found.company()));
            if (matching == null) {
                dropped++;
                continue;
            }
            var evidence = checker.evidenceFor(found.quote());
            if (evidence.isEmpty()) {
                dropped++;
                continue;
            }
            for (Relationship relationship : matching) {
                if (relationship.getCriticality() != null) continue;
                verified.put(relationship.getId(), new CriticalityResultSaver.VerifiedCriticality(
                        found.criticality(), evidence.get()));
            }
        }
        resultSaver.save(reportId, asked.callId(), verified, dropped);
    }

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
        AiAnswer answer = gemini.generateCriticality(request);
        try {
            return new Asked(answers.readCriticalities(answer.text()), answer.callId());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId());
            throw exception;
        }
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record Asked(List<GeminiAnswer.CriticalityFound> found, Long callId) { }
}
