package com.truesight.ai;

import com.truesight.common.ApiException;
import com.truesight.report.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the calls already recorded by {@link AiCallLog} for the Analyst Desk. */
@Service
public class AiCallService {

    static final int PAGE_SIZE = 50;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("startedAt"), Sort.Order.desc("id"));

    private final AiCallRepository calls;
    private final ReportRepository reports;

    public AiCallService(AiCallRepository calls, ReportRepository reports) {
        this.calls = calls;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public AiCallPageResponse list(AiCallQuery query, int page) {
        validate(query, page);
        Page<AiCall> found = calls.findAll(
                query.toSpecification(), PageRequest.of(page, PAGE_SIZE, NEWEST_FIRST));
        return new AiCallPageResponse(
                found.getContent().stream().map(AiCallService::summary).toList(),
                found.getNumber(), found.getTotalPages(), found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AiCallDetailResponse detail(Long id) {
        AiCall call = calls.findById(id)
                .orElseThrow(() -> ApiException.notFound("That AI call was not found."));
        String reportUrl = call.getReportId() == null
                ? null
                : reports.findById(call.getReportId()).map(report -> report.getUrl()).orElse(null);
        return new AiCallDetailResponse(
                call.getId(), call.getStartedAt(), call.getPurpose(), call.getModel(), call.getOutcome(),
                call.getDurationMs(), call.getKept(), call.getDropped(), call.getPrompt(), call.getResponse(),
                call.getError(), call.getReportId(), reportUrl);
    }

    @Transactional(readOnly = true)
    public byte[] csv(AiCallQuery query) {
        validate(query, 0);
        List<AiCall> found = calls.findAll(query.toSpecification(), NEWEST_FIRST);
        StringBuilder csv = new StringBuilder()
                .append("Started At,Purpose,Model,Outcome,Duration (ms),Kept,Dropped\r\n");
        found.forEach(call -> csv
                .append(value(call.getStartedAt())).append(',')
                .append(value(call.getPurpose())).append(',')
                .append(value(call.getModel())).append(',')
                .append(value(call.getOutcome())).append(',')
                .append(value(call.getDurationMs())).append(',')
                .append(value(call.getKept())).append(',')
                .append(value(call.getDropped())).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static AiCallSummaryResponse summary(AiCall call) {
        return new AiCallSummaryResponse(
                call.getId(), call.getStartedAt(), call.getPurpose(), call.getModel(), call.getOutcome(),
                call.getDurationMs(), call.getKept(), call.getDropped());
    }

    private static String value(Object value) {
        if (value == null) return "";
        return '"' + value.toString().replace("\"", "\"\"") + '"';
    }

    private static void validate(AiCallQuery query, int page) {
        if (page < 0) {
            throw ApiException.badRequest("The page must be zero or greater.");
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw ApiException.badRequest("The From date must not be after the To date.");
        }
    }
}
