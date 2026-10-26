package com.truesight.relationship;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The analyst-run pass: sends every relationship that has no second check yet, report by report, so each call is
 * recorded against its report. A relationship with a second check is never sent again, so a second run sends nothing.
 * Relationships whose check failed, or was unreadable, have none, so the next run tries them again.
 */
@Service
public class SecondCheckPassService {

    private final RelationshipRepository relationships;
    private final SecondCheck secondCheck;
    private final SecondCheckSaver saver;

    public SecondCheckPassService(RelationshipRepository relationships, SecondCheck secondCheck,
                                  SecondCheckSaver saver) {
        this.relationships = relationships;
        this.secondCheck = secondCheck;
        this.saver = saver;
    }

    /** The reports that have a relationship with no second check. */
    public List<Long> pendingReportIds() {
        return relationships.findReportIdsWithoutSecondCheck();
    }

    /** Checks one report's unchecked relationships. Asking Gemini holds no database connection. */
    public void check(Long reportId) {
        List<Relationship> pending = relationships.findByReportIdAndSecondCheckIsNullOrderByIdAsc(reportId);
        Map<Long, SecondCheck.Result> results = secondCheck.ask(pending, reportId);
        if (!results.isEmpty()) {
            saver.save(results);
        }
    }
}
