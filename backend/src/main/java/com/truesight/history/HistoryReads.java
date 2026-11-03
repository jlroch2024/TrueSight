package com.truesight.history;

import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.CompanyRepository;
import com.truesight.relationship.PastRelationshipRepository;
import com.truesight.report.ReportInfo;
import com.truesight.report.ReportRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What of a company's earlier annual reports has been read and what is still to read. A holding's two previous reports
 * are the two filed before its latest. A report counts as read once it has older relationships or a completed
 * timeline answer, even one that named nothing, so it is never read twice.
 */
@Component
class HistoryReads {

    /** How many earlier reports are read for each holding. */
    static final int EARLIER = 2;

    private final ReportRepository reports;
    private final PastRelationshipRepository pastRelationships;
    private final AiCallRepository aiCalls;
    private final CompanyRepository companies;

    HistoryReads(ReportRepository reports, PastRelationshipRepository pastRelationships, AiCallRepository aiCalls,
                 CompanyRepository companies) {
        this.reports = reports;
        this.pastRelationships = pastRelationships;
        this.aiCalls = aiCalls;
        this.companies = companies;
    }

    /** Each company's saved reports, newest first. */
    Map<Long, List<ReportInfo>> reportsByCompany(Collection<Long> companyIds) {
        Map<Long, List<ReportInfo>> byCompany = new HashMap<>();
        if (companyIds.isEmpty()) return byCompany;
        for (ReportInfo report : reports.findInfoByCompanyIdIn(companyIds)) {
            byCompany.computeIfAbsent(report.getCompanyId(), id -> new ArrayList<>()).add(report);
        }
        byCompany.values().forEach(list -> list.sort(Comparator.comparing(ReportInfo::getFilingDate).reversed()));
        return byCompany;
    }

    boolean isRead(Long reportId) {
        return pastRelationships.existsByReportId(reportId)
                || aiCalls.existsByReportIdAndPurposeAndOutcomeAndKeptIsNotNull(
                reportId, AiPurpose.TIMELINE, AiOutcome.ANSWERED);
    }

    /** The reports filed before the company's latest, newest first. {@code newestFirst} must be its saved reports. */
    static List<ReportInfo> earlier(List<ReportInfo> newestFirst) {
        if (newestFirst.isEmpty()) return List.of();
        ReportInfo latest = newestFirst.get(0);
        return newestFirst.stream().filter(report -> report.getFilingDate().isBefore(latest.getFilingDate())).toList();
    }

    /**
     * How many earlier reports this company has to read: the ones saved, if any (they are saved all together, after
     * every one has downloaded, so those are all there are), otherwise how many the SEC had when last asked, which is
     * kept on the company so a restart does not forget it, otherwise two, until the SEC has been asked.
     */
    int expected(Long companyId, List<ReportInfo> newestFirst) {
        int saved = Math.min(EARLIER, earlier(newestFirst).size());
        if (saved > 0) return saved;
        Integer asked = companies.findEarlierReportsById(companyId);
        return asked == null ? EARLIER : Math.min(EARLIER, asked);
    }

    /** How many of the company's earlier reports are still to read. */
    int unread(Long companyId, List<ReportInfo> newestFirst) {
        long read = earlier(newestFirst).stream().limit(EARLIER).filter(report -> isRead(report.getId())).count();
        return (int) Math.max(0, expected(companyId, newestFirst) - read);
    }
}
