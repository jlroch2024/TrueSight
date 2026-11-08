package com.truesight.relationship;

import com.truesight.ai.AiAnswer;
import com.truesight.ai.AiCallLog;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.ai.AiRequest;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyName;
import com.truesight.company.CompanyNameRepository;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.report.ReportTextExtractor;
import com.truesight.report.SecClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the other companies whose annual reports name a holding as their supplier or customer: the relationships the
 * holding's own report leaves out, such as the suppliers who call Apple their largest customer.
 *
 * <ol>
 *   <li>{@link #due}: a company is searched at most once every {@value #DAYS_BETWEEN_SEARCHES} days, whichever
 *       portfolio holds it.</li>
 *   <li>{@link #filingsFor}: the SEC's full-text search for the holding's display name, in quotes, in 10-Ks and 20-Fs
 *       of the last {@value #MONTHS_SEARCHED} months. Only the reports themselves, never their exhibits or the
 *       holding's own; at most one per company, and at most {@value #MAX_FILINGS}, in the order the search gives.</li>
 *   <li>{@link #read}: each filing is its company's report, the stored one if there is one. Only the passages that name
 *       the holding beside a supplier or customer word are sent to the AI ({@link ReportSection#passagesNaming}); a
 *       report with none (most just name a competitor) is not. Each quote is checked against the report's own words,
 *       and each proven one is saved as an ordinary relationship of that report, marked {@link FoundBy#NAME_SEARCH}.</li>
 *   <li>{@link #record}: the search is recorded, with how many filings it gave.</li>
 * </ol>
 *
 * <p>{@code NameSearchJob} runs these in the background once a holding is Done, so they never hold up its status.
 */
@Service
public class NameSearcher {

    static final int DAYS_BETWEEN_SEARCHES = 90;
    static final int MONTHS_SEARCHED = 15;
    static final int MAX_FILINGS = 25;
    static final int MAX_PASSAGES = 10;
    static final int MAX_PASSAGE_CHARACTERS = 8_000;
    /** The longest "provides" the relationships table accepts. */
    private static final int MAX_PROVIDES = 500;

    /** The holding is named in quotes, so the search finds the whole name, not its words apart. */
    static final String PROMPT = """
            Below are passages from %1$s's annual report (%2$s) that mention %3$s. Say whether they name %3$s as a \
            supplier to %1$s or as a customer of %1$s, give what is supplied in a few words, and copy one sentence \
            that shows it. Answer with an empty list if they only name %3$s as a competitor, partner or anything else.

            PASSAGES:
            %4$s""";

    /** A list of what the passages say: SUPPLIER means the holding supplies the company whose report it is. */
    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "relationship", Map.of("type", "STRING", "enum", List.of("SUPPLIER", "CUSTOMER")),
                            "provides", Map.of("type", "STRING"),
                            "quote", Map.of("type", "STRING")),
                    "required", List.of("relationship", "provides", "quote"),
                    "propertyOrdering", List.of("relationship", "provides", "quote")));

    private final HoldingRepository holdings;
    private final CompanyRepository companies;
    private final CompanyNameRepository companyNames;
    private final CompanyDirectory directory;
    private final CompanyDisplayNames displayNames;
    private final ReportRepository reports;
    private final RelationshipRepository relationships;
    private final NameSearchRepository searches;
    private final AiCallRepository aiCalls;
    private final AiCallLog calls;
    private final GeminiClient gemini;
    private final GeminiAnswer answers;
    private final SecClient sec;

    public NameSearcher(HoldingRepository holdings, CompanyRepository companies, CompanyNameRepository companyNames,
                        CompanyDirectory directory, CompanyDisplayNames displayNames, ReportRepository reports,
                        RelationshipRepository relationships, NameSearchRepository searches, AiCallRepository aiCalls,
                        AiCallLog calls, GeminiClient gemini, GeminiAnswer answers, SecClient sec) {
        this.holdings = holdings;
        this.companies = companies;
        this.companyNames = companyNames;
        this.directory = directory;
        this.displayNames = displayNames;
        this.reports = reports;
        this.relationships = relationships;
        this.searches = searches;
        this.aiCalls = aiCalls;
        this.calls = calls;
        this.gemini = gemini;
        this.answers = answers;
        this.sec = sec;
    }

    /**
     * The holding's company, if it is due a search: the holding is Done, and its company has not been searched for in
     * the last {@value #DAYS_BETWEEN_SEARCHES} days.
     */
    @Transactional(readOnly = true)
    public Optional<Holder> due(Long holdingId) {
        Holding holding = holdings.findById(holdingId).orElse(null);
        if (holding == null || holding.getStatus() != HoldingStatus.DONE || holding.getCompanyId() == null) {
            return Optional.empty();
        }
        return due(holding.getCompanyId(), Instant.now());
    }

    /** As {@link #due(Long)}, for the holding's company: empty if it was searched for since {@code now} less 90 days. */
    @Transactional(readOnly = true)
    public Optional<Holder> due(Long companyId, Instant now) {
        boolean recent = searches.findById(companyId)
                .filter(search -> search.getSearchedAt().isAfter(now.minus(Duration.ofDays(DAYS_BETWEEN_SEARCHES))))
                .isPresent();
        if (recent) {
            return Optional.empty();
        }
        return companies.findById(companyId).map(company -> {
            String displayName = displayNames.of(company);
            Set<String> names = new LinkedHashSet<>(List.of(displayName, company.getName()));
            companyNames.findByCompanyId(companyId).stream().map(CompanyName::getName).forEach(names::add);
            return new Holder(companyId, company.getCik(), displayName, List.copyOf(names));
        });
    }

    /**
     * Searches the SEC for other companies' reports that mention the holding, up to {@code today}.
     *
     * @throws SecClient.SecAccessException if the SEC cannot be reached
     */
    public Filings filingsFor(Holder holder, LocalDate today) {
        List<SecClient.SecSearchHit> hits = sec.searchAnnualReports(
                holder.displayName(), today.minusMonths(MONTHS_SEARCHED), today);
        Set<String> filings = new LinkedHashSet<>();
        Set<String> filers = new LinkedHashSet<>();
        List<SecClient.SecSearchHit> toRead = new ArrayList<>();
        for (SecClient.SecSearchHit hit : hits) {
            if (!hit.isTheReport() || sameCik(hit.cik(), holder.cik())) {
                continue; // an exhibit, such as a pay plan naming peer companies, or the holding's own report
            }
            filings.add(hit.accessionNumber());
            if (filers.add(hit.cik()) && toRead.size() < MAX_FILINGS) {
                toRead.add(hit);
            }
        }
        return new Filings(filings.size(), toRead);
    }

    /** As {@link #filingsFor(Holder, LocalDate)}, up to today. */
    public Filings filingsFor(Holder holder) {
        return filingsFor(holder, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Reads one filing for the holding, and saves the relationships its passages prove. Skipped when the report has
     * been read for this holding already, or has no passage naming it beside a supplier or customer word.
     *
     * @return the report's id if a relationship was saved (for the second check), otherwise null
     * @throws SecClient.SecAccessException if the filing cannot be downloaded
     * @throws GeminiClient.GeminiException if the AI fails
     */
    // An AI failure must not undo the work before it: the downloaded report stays stored.
    @Transactional(noRollbackFor = GeminiClient.GeminiException.class)
    public Long read(Holder holder, SecClient.SecSearchHit hit) {
        Company company = directory.findOrCreate(hit.companyName(), hit.cik(), hit.ticker(), List.of());
        if (company.getId().equals(holder.companyId())) {
            return null;
        }
        Report report = reportOf(company, hit);
        if (readFor(holder, report)) {
            return null;
        }
        List<String> passages = ReportSection.passagesNaming(report.getText(), holder.names(), MAX_PASSAGES,
                MAX_PASSAGE_CHARACTERS);
        if (passages.isEmpty()) {
            return null;
        }
        String passageText = String.join("\n", passages);
        AiRequest request = new AiRequest(AiPurpose.NAME_SEARCH,
                prompt(company.getName(), report.getForm(), holder.displayName(), passages), report.getId(),
                prompt(company.getName(), report.getForm(), holder.displayName(),
                        List.of(AiRequest.reportMarker(report.getId(), passageText))));
        AiAnswer answer = gemini.generate(request, RESPONSE_SCHEMA);
        List<GeminiAnswer.NameSearchFound> found;
        try {
            found = answers.readNameSearch(answer.text());
        } catch (GeminiAnswer.UnreadableAnswerException exception) {
            calls.unreadable(answer.callId()); // not counted as read: the next search asks again
            return null;
        }

        QuoteChecker checker = new QuoteChecker(report.getText());
        List<Relationship> proven = new ArrayList<>();
        int dropped = 0;
        for (GeminiAnswer.NameSearchFound item : found) {
            Optional<QuoteChecker.QuoteMatch> match = checker.match(item.quote());
            if (match.isEmpty()) {
                dropped++;
                continue;
            }
            if (relationships.existsByReportIdAndCounterpartyIdAndType(report.getId(), holder.companyId(),
                    item.relationship()) || proven.stream().anyMatch(saved -> saved.getType() == item.relationship())) {
                continue; // the report names the holding this way already
            }
            // SUPPLIER: the holding supplies the company whose report it is, as a relationship's counterparty does.
            Relationship relationship = new Relationship(report.getId(), company.getId(), holder.companyId(),
                    item.relationship(), item.provides().isEmpty() ? null : cut(item.provides()),
                    match.get().evidence());
            relationship.setQuoteMatch(match.get().recordedShare());
            relationship.setFoundBy(FoundBy.NAME_SEARCH);
            proven.add(relationship);
        }
        relationships.saveAll(proven);
        calls.finish(answer.callId(), proven.size(), dropped);
        return proven.isEmpty() ? null : report.getId();
    }

    /** Records that the holding's company was searched for {@code at}, and how many filings the search gave. */
    @Transactional
    public void record(Long companyId, int filingsFound, Instant at) {
        NameSearch search = searches.findById(companyId).orElse(null);
        if (search == null) {
            searches.save(new NameSearch(companyId, at, filingsFound));
        } else {
            search.searchedAgain(at, filingsFound);
        }
    }

    /** The prompt for these passages of {@code company}'s report, which mention {@code holding}. */
    static String prompt(String company, String form, String holding, List<String> passages) {
        return PROMPT.formatted(company, form, holding, String.join("\n", passages));
    }

    /** The company's stored report, or the filing downloaded and stored as its report, like any report. */
    private Report reportOf(Company company, SecClient.SecSearchHit hit) {
        return reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId())
                .or(() -> reports.findByAccessionNumber(hit.accessionNumber()))
                .orElseGet(() -> reports.save(new Report(company.getId(), hit.form(), hit.accessionNumber(),
                        hit.filingDate(), hit.url(), ReportTextExtractor.extract(sec.download(hit.url())))));
    }

    /**
     * Whether the report has been read for this holding: it names the holding already, or a name search for the holding
     * has been answered for it. Its recorded prompt names the holding, never the report's text.
     */
    private boolean readFor(Holder holder, Report report) {
        return relationships.existsByReportIdAndCounterpartyId(report.getId(), holder.companyId())
                || aiCalls.existsByReportIdAndPurposeAndOutcomeAndPromptContaining(report.getId(),
                AiPurpose.NAME_SEARCH, AiOutcome.ANSWERED, "that mention " + holder.displayName() + ". Say");
    }

    /** Two SEC numbers are the same company however many leading zeros each has. */
    private static boolean sameCik(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceFirst("^0+", "").equals(b.replaceFirst("^0+", ""));
    }

    private static String cut(String value) {
        return value.length() <= MAX_PROVIDES ? value : value.substring(0, MAX_PROVIDES);
    }

    /**
     * A holding's company, as a name search sees it: its id, its SEC number, the display name searched for, and every
     * name it is known by, for finding the passages that name it.
     */
    public record Holder(Long companyId, String cik, String displayName, List<String> names) {
    }

    /** What a search gave: how many filings (other companies' reports), and the ones to read. */
    public record Filings(int found, List<SecClient.SecSearchHit> toRead) {
    }
}
