package com.truesight.report;

import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.company.ReportStatus;
import com.truesight.config.WorkerHeartbeat;
import com.truesight.news.NewsCheckService;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.relationship.GeminiClient;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.ReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the annual report of every supplier of a holding, one level deeper than the holding's own, so the graph and
 * the news check can see who supplies the holding's suppliers.
 *
 * <ul>
 *   <li>Only suppliers: a disruption flows down the chain to customers, so a holding's customers are not read.</li>
 *   <li>Only one level: a supplier's own suppliers are found in its report, but their reports are not read.</li>
 *   <li>Reports are shared: a supplier whose report is stored, that has a {@code report_status} already, or that is a
 *       holding somewhere (and so read as one) is never read again.</li>
 *   <li>Each supplier is read by {@link ReportReader}, exactly as a holding is, and its {@code report_status} records
 *       how it went: Done, No Report Found (no ticker, or the SEC does not know it), or Failed with the reason.</li>
 * </ul>
 *
 * <p>The analysis calls {@link #queue} as it marks a holding Done, in the same transaction, so the holding's suppliers
 * are counted as waiting from the moment it is Done. {@code AnalysisJob} then calls {@link #read} for each of
 * {@link #waitingFor}, one by one, on the analysis executor, so the holding's own status never waits for them.
 */
@Service
public class SupplierReader {

    /** The statuses of a supplier whose reading has finished, however it went. */
    private static final Set<ReportStatus> FINISHED =
            Set.of(ReportStatus.DONE, ReportStatus.NO_REPORT_FOUND, ReportStatus.FAILED);

    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final ReportRepository reports;
    private final ReportReader reportReader;
    private final WorkerHeartbeat heartbeat;

    public SupplierReader(HoldingRepository holdings, RelationshipRepository relationships,
                          CompanyRepository companies, ReportRepository reports, ReportReader reportReader,
                          WorkerHeartbeat heartbeat) {
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.reports = reports;
        this.reportReader = reportReader;
        this.heartbeat = heartbeat;
    }

    /**
     * Sets the suppliers of a holding's company to Waiting, leaving out every supplier that is never read again (see
     * the class), and records this server as their owner. Returns the ids it set Waiting.
     */
    @Transactional
    public List<Long> queue(Long holdingCompanyId) {
        List<Company> queued = companies.findAllById(suppliersOf(List.of(holdingCompanyId))).stream()
                .filter(company -> company.getReportStatus() == null)
                .filter(company -> reports.findLatestIdByCompanyId(company.getId()).isEmpty())
                .filter(company -> !holdings.existsByCompanyId(company.getId()))
                .toList();
        queued.forEach(company -> {
            record(company, ReportStatus.WAITING, null);
            company.setReportWorkerId(heartbeat.id());
        });
        return queued.stream().map(Company::getId).toList();
    }

    /**
     * The company ids of a Done holding's suppliers that are waiting to be read, oldest first; none if the holding is
     * not Done. A supplier shared with another holding may be waiting for that holding's job too: {@link #read} reads
     * it once.
     */
    @Transactional(readOnly = true)
    public List<Long> waitingFor(Long holdingId) {
        Holding holding = holdings.findById(holdingId).orElse(null);
        if (holding == null || holding.getStatus() != HoldingStatus.DONE || holding.getCompanyId() == null) {
            return List.of();
        }
        return companies.findAllById(suppliersOf(List.of(holding.getCompanyId()))).stream()
                .filter(company -> company.getReportStatus() == ReportStatus.WAITING)
                .map(Company::getId)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Reads one waiting supplier's annual report, and records how it went. A supplier no longer waiting (read
     * meanwhile for another holding) is left alone.
     */
    @Transactional
    public void read(Long companyId) {
        Company supplier = companies.findById(companyId).orElse(null);
        if (supplier == null || supplier.getReportStatus() != ReportStatus.WAITING) {
            return;
        }
        supplier.setReportWorkerId(heartbeat.id());
        try {
            Company filer = supplier.getTicker() == null ? null : reportReader.companyFor(supplier.getTicker());
            if (filer == null || !reportReader.read(filer)) {
                record(supplier, ReportStatus.NO_REPORT_FOUND, null);
                return;
            }
            record(supplier, ReportStatus.DONE, null);
        } catch (SecClient.SecAccessException | GeminiClient.GeminiException exception) {
            record(supplier, ReportStatus.FAILED, exception.getMessage());
        } catch (RuntimeException exception) {
            record(supplier, ReportStatus.FAILED, "The annual report could not be processed.");
        }
    }

    /**
     * Marks a supplier Failed when {@link #read} stopped on an error it could not record itself (its transaction was
     * rolled back), so the supplier is never left waiting.
     */
    @Transactional
    public void fail(Long companyId) {
        companies.findById(companyId).ifPresent(company ->
                record(company, ReportStatus.FAILED, "The annual report could not be processed."));
    }

    /** The supplier's ticker, which {@code AnalysisJob} locks on so it is never read twice at once; null if none. */
    @Transactional(readOnly = true)
    public String tickerOf(Long companyId) {
        return companies.findById(companyId).map(Company::getTicker).orElse(null);
    }

    /**
     * The companies that supply any of these companies, from either company's report. A rejected relationship is
     * wrong, so it names no supplier.
     */
    @Transactional(readOnly = true)
    public Set<Long> suppliersOf(Collection<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return Set.of();
        }
        return relationships.findByCompanyIdInOrCounterpartyIdIn(companyIds, companyIds).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .filter(relationship -> companyIds.contains(NewsCheckService.customerId(relationship)))
                .map(NewsCheckService::supplierId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * How far reading these holdings' suppliers has got: how many have finished, of how many were asked for. A
     * supplier never asked for (it is a holding, or its report was stored already) is in neither count.
     */
    @Transactional(readOnly = true)
    public Progress progressOf(Collection<Long> holdingCompanyIds) {
        List<ReportStatus> statuses = companies.findAllById(suppliersOf(holdingCompanyIds)).stream()
                .map(Company::getReportStatus)
                .filter(status -> status != null)
                .toList();
        int read = (int) statuses.stream().filter(FINISHED::contains).count();
        return new Progress(read, statuses.size());
    }

    private static void record(Company company, ReportStatus status, String reason) {
        company.setReportStatus(status);
        company.setReportStatusReason(reason);
    }

    /** How many suppliers' reports are read, of how many are to be read. */
    public record Progress(int read, int toRead) {
    }
}
