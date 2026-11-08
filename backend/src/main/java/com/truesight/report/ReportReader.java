package com.truesight.report;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.relationship.RelationshipFinder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Finds, downloads and reads a company's latest annual report: the one way a report is read, for a holding and for a
 * holding's supplier alike, so the two never differ.
 *
 * <ol>
 *   <li>{@link #companyFor} looks the ticker up at the SEC, and finds or creates the company it names.</li>
 *   <li>{@link #read} downloads the company's latest 10-K or 20-F, unless it is stored already, and has
 *       {@link RelationshipFinder} find the suppliers and customers it names.</li>
 * </ol>
 *
 * <p>Not transactional itself: it runs inside its caller's transaction, so a failure here never stops the caller
 * from recording it.
 */
@Component
public class ReportReader {

    private final SecClient sec;
    private final CompanyDirectory companies;
    private final ReportRepository reports;
    private final RelationshipFinder relationshipFinder;

    public ReportReader(SecClient sec, CompanyDirectory companies, ReportRepository reports,
                        RelationshipFinder relationshipFinder) {
        this.sec = sec;
        this.companies = companies;
        this.reports = reports;
        this.relationshipFinder = relationshipFinder;
    }

    /** The company the SEC knows by this ticker, or null if it knows none. */
    public Company companyFor(String ticker) {
        SecClient.SecCompany secCompany = sec.companyForTicker(ticker);
        if (secCompany == null) {
            return null;
        }
        return companies.findOrCreate(secCompany.name(), secCompany.cik(), secCompany.ticker(), List.of());
    }

    /**
     * Reads the company's latest annual report and saves the relationships it names. A report already stored is
     * never downloaded again, and one already read is never sent to the AI again.
     *
     * @return false if the company has no 10-K or 20-F
     * @throws SecClient.SecAccessException if the SEC cannot be reached
     * @throws com.truesight.relationship.GeminiClient.GeminiException if the AI fails, or the day's limit on reading
     *         reports is reached (a {@link com.truesight.relationship.ReadingLimit.ReachedException})
     */
    public boolean read(Company company) {
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId()).orElse(null);
        if (report == null) {
            // A report not stored yet has to be sent to the AI once it is: past the day's limit, do not download it.
            relationshipFinder.requireRoomToRead();
            SecClient.SecFiling filing = sec.latestAnnualFiling(company.getCik());
            if (filing == null) {
                return false;
            }
            String text = ReportTextExtractor.extract(sec.download(filing.url()));
            report = reports.save(new Report(company.getId(), filing.form(), filing.accessionNumber(),
                    filing.filingDate(), filing.url(), text));
        }
        boolean refreshed = false;
        if (ReportTextExtractor.hasLegacyWrappedParagraphs(report.getText())) {
            String currentText = ReportTextExtractor.extract(sec.download(report.getUrl()));
            if (!currentText.isBlank() && !currentText.equals(report.getText())) {
                report.setText(currentText);
                reports.save(report);
                refreshed = true;
            }
        }
        // A changed report gets one new assessment; unchanged answered reports remain idempotent.
        relationshipFinder.findIn(report, company, refreshed);
        return true;
    }
}
