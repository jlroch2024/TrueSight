package com.truesight.report;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads a report's dependency figures once, from the tagged figures (XBRL) filed beside it, and saves them. No AI
 * is involved.
 *
 * <p>Reading figures never fails a holding. The download and the parsing, which are what can fail, run first and
 * are caught: if the SEC cannot be reached or the files cannot be read, the report stays unread for figures
 * ({@code figuresReadAt} stays null) and the next Analyse tries again. The rows are then saved in the caller's
 * transaction, because the report itself may not be committed yet and the figures refer to it.
 */
@Service
public class FigureService {

    private static final Logger log = LoggerFactory.getLogger(FigureService.class);

    private final SecClient sec;
    private final CompanyDirectory companies;
    private final ReportRepository reports;
    private final ConcentrationRepository concentrations;

    public FigureService(SecClient sec, CompanyDirectory companies, ReportRepository reports,
                         ConcentrationRepository concentrations) {
        this.sec = sec;
        this.companies = companies;
        this.reports = reports;
        this.concentrations = concentrations;
    }

    /** Reads and saves the report's figures, unless they were read already. Never throws for a SEC or file problem. */
    public void read(Report report, Company company) {
        if (report.getFiguresReadAt() != null) return;

        List<ConcentrationReader.Figure> figures;
        try {
            Optional<SecClient.TaggedFigures> tagged = sec.taggedFigures(report);
            figures = tagged == null || tagged.isEmpty()
                    ? List.of()
                    : ConcentrationReader.read(tagged.get().instance(), tagged.get().labels());
        } catch (RuntimeException failure) {
            log.warn("Dependency figures of report {} could not be read; they will be tried again: {}",
                    report.getId(), failure.getMessage());
            return;
        }

        for (ConcentrationReader.Figure figure : figures) {
            Long counterpartyId = figure.named()
                    ? companies.findOrCreate(figure.label(), null, null, List.of()).getId()
                    : null;
            concentrations.save(new Concentration(report.getId(), company.getId(), counterpartyId, figure.label(),
                    figure.kind(), figure.measure(), figure.share(), figure.periodStart(), figure.periodEnd()));
        }
        report.setFiguresReadAt(Instant.now());
        reports.save(report);
    }
}
