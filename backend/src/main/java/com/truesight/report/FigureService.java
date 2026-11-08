package com.truesight.report;

import com.truesight.company.CompanyDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads a report's dependency figures once, from the tagged figures (XBRL) filed beside it, and saves them. No AI
 * is involved.
 *
 * <p>Reading figures never fails a holding, and never undoes its relationships. {@code AnalysisJob} calls this after
 * the holding's own transaction has committed, so the figures are saved in a transaction of their own: whatever goes
 * wrong here (the SEC, the files, the database) is caught and logged, and only the figures are lost.
 * <ul>
 *   <li>If the SEC cannot be reached, or the figures cannot be saved, the report stays unread
 *       ({@code figuresReadAt} stays null) and the next Analyse tries again.</li>
 *   <li>If the files cannot be read as XML, that will not change by asking again: the report is marked read with no
 *       figures, so it is not downloaded at every Analyse.</li>
 * </ul>
 */
@Service
public class FigureService {

    private static final Logger log = LoggerFactory.getLogger(FigureService.class);

    private final SecClient sec;
    private final CompanyDirectory companies;
    private final ReportRepository reports;
    private final ConcentrationRepository concentrations;
    private final TransactionTemplate transaction;

    public FigureService(SecClient sec, CompanyDirectory companies, ReportRepository reports,
                         ConcentrationRepository concentrations, PlatformTransactionManager transactionManager) {
        this.sec = sec;
        this.companies = companies;
        this.reports = reports;
        this.concentrations = concentrations;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Reads and saves the figures of the company's latest report, unless they were read already or it has no report.
     * Never throws.
     */
    public void readLatestOf(Long companyId) {
        try {
            Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(companyId).orElse(null);
            if (report == null || report.getFiguresReadAt() != null) return;
            read(report);
        } catch (RuntimeException failure) {
            log.warn("Dependency figures of company {} could not be read; they will be tried again: {}",
                    companyId, failure.getMessage());
        }
    }

    private void read(Report report) {
        List<ConcentrationReader.Figure> figures;
        try {
            Optional<SecClient.TaggedFigures> tagged = sec.taggedFigures(report);
            figures = tagged == null || tagged.isEmpty()
                    ? List.of()
                    : ConcentrationReader.read(tagged.get().instance(), tagged.get().labels());
        } catch (IllegalArgumentException unreadable) {
            log.warn("The tagged figures of report {} cannot be read, so it is marked read with none: {}",
                    report.getId(), unreadable.getMessage());
            figures = List.of();
        } catch (RuntimeException unreachable) {
            log.warn("Dependency figures of report {} could not be downloaded; they will be tried again: {}",
                    report.getId(), unreachable.getMessage());
            return;
        }
        save(report.getId(), report.getCompanyId(), figures);
    }

    /** Saves the rows and marks the report read in one transaction: all of it, or none of it. */
    private void save(Long reportId, Long companyId, List<ConcentrationReader.Figure> figures) {
        try {
            transaction.executeWithoutResult(status -> {
                for (ConcentrationReader.Figure figure : figures) {
                    Long counterpartyId = figure.named()
                            ? companies.findOrCreate(figure.label(), null, null, List.of()).getId()
                            : null;
                    concentrations.save(new Concentration(reportId, companyId, counterpartyId, figure.label(),
                            figure.kind(), figure.measure(), figure.share(), figure.periodStart(), figure.periodEnd()));
                }
                Report report = reports.findById(reportId).orElseThrow();
                report.setFiguresReadAt(Instant.now());
                reports.save(report);
            });
        } catch (RuntimeException failure) {
            log.warn("Dependency figures of report {} could not be saved; they will be tried again: {}",
                    reportId, failure.getMessage());
        }
    }
}
