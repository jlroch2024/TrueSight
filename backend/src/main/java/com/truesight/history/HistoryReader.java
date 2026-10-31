package com.truesight.history;

import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipFinder;
import com.truesight.report.Report;
import com.truesight.report.ReportInfo;
import com.truesight.report.ReportRepository;
import com.truesight.report.ReportTextExtractor;
import com.truesight.report.SecClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads each holding's two previous annual reports: the work behind Load History.
 *
 * <p>For each holding whose earlier reports are not all read, it asks the SEC for the company's annual reports, saves
 * the two filed before the latest (as ordinary reports), and has the AI read each for the relationships it names, with
 * the same prompt and quote check as the latest report ({@link RelationshipFinder#findPast}). A report already read is
 * skipped, so a second run reads nothing it read before. If the SEC or the AI fails it stops, says why, and keeps
 * everything read so far; running it again carries on.
 */
@Service
public class HistoryReader {

    private static final Logger log = LoggerFactory.getLogger(HistoryReader.class);

    private final HoldingRepository holdings;
    private final CompanyRepository companies;
    private final ReportRepository reports;
    private final SecClient sec;
    private final RelationshipFinder finder;
    private final HistoryReads reads;
    private final HistoryProgress progress;
    private final ConcurrentHashMap<Long, Object> companyLocks = new ConcurrentHashMap<>();

    HistoryReader(HoldingRepository holdings, CompanyRepository companies, ReportRepository reports, SecClient sec,
                  RelationshipFinder finder, HistoryReads reads, HistoryProgress progress) {
        this.holdings = holdings;
        this.companies = companies;
        this.reports = reports;
        this.sec = sec;
        this.finder = finder;
        this.reads = reads;
        this.progress = progress;
    }

    /** Reads what is missing for this portfolio, updating its progress as it goes. */
    public void read(Long portfolioId) {
        try {
            for (Long companyId : companiesToRead(portfolioId)) {
                readCompany(portfolioId, companyId);
            }
            progress.finish(portfolioId);
        } catch (SecClient.SecAccessException | GeminiClient.GeminiException exception) {
            progress.fail(portfolioId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Reading the earlier annual reports of portfolio {} failed", portfolioId, exception);
            progress.fail(portfolioId, "The earlier annual reports could not be read. Please try again.");
        }
    }

    /** The companies of the portfolio's analysed holdings that still have earlier reports to read. */
    private Set<Long> companiesToRead(Long portfolioId) {
        Set<Long> companyIds = new LinkedHashSet<>();
        for (Holding holding : holdings.findByPortfolioIdOrderByTickerAsc(portfolioId)) {
            if (holding.getCompanyId() != null && holding.getStatus() == HoldingStatus.DONE) {
                companyIds.add(holding.getCompanyId());
            }
        }
        Map<Long, List<ReportInfo>> byCompany = reads.reportsByCompany(companyIds);
        companyIds.removeIf(id -> reads.unread(id, byCompany.getOrDefault(id, List.of())) == 0);
        return companyIds;
    }

    private void readCompany(Long portfolioId, Long companyId) {
        // Two portfolios holding the same company must not read its reports twice.
        synchronized (companyLocks.computeIfAbsent(companyId, ignored -> new Object())) {
            Company company = companies.findById(companyId).orElse(null);
            if (company == null || company.getCik() == null) return;
            List<ReportInfo> saved = reads.reportsByCompany(List.of(companyId)).getOrDefault(companyId, List.of());
            if (saved.isEmpty()) return;
            ReportInfo latest = saved.get(0);

            List<SecClient.SecFiling> earlier = sec.annualFilings(company.getCik(), HistoryReads.EARLIER + 2).stream()
                    .filter(filing -> filing.filingDate().isBefore(latest.getFilingDate()))
                    .limit(HistoryReads.EARLIER)
                    .toList();
            reads.available(companyId, earlier.size());

            for (SecClient.SecFiling filing : earlier) {
                Report report = reports.findByAccessionNumber(filing.accessionNumber()).orElseGet(() -> reports.save(
                        new Report(companyId, filing.form(), filing.accessionNumber(), filing.filingDate(),
                                filing.url(), ReportTextExtractor.extract(sec.download(filing.url())))));
                if (reads.isRead(report.getId())) continue;
                finder.findPast(report, company);
                progress.advance(portfolioId);
            }
        }
    }
}
