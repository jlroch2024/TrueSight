package com.truesight.relationship;

import com.truesight.common.DatabaseLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final DatabaseLock lock;
    private final TransactionTemplate transaction;

    public SecondCheckPassService(RelationshipRepository relationships, SecondCheck secondCheck,
                                  SecondCheckSaver saver, DatabaseLock lock, PlatformTransactionManager transactions) {
        this.relationships = relationships;
        this.secondCheck = secondCheck;
        this.saver = saver;
        this.lock = lock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** The reports that have a relationship with no second check. */
    public List<Long> pendingReportIds() {
        return relationships.findReportIdsWithoutSecondCheck();
    }

    /**
     * Checks one report's unchecked relationships unless another job, on any server, is checking that report now: two
     * holdings of one company share a report, and whoever gets there second has nothing left to do.
     */
    public void checkOnce(Long reportId) {
        transaction.executeWithoutResult(status -> {
            if (lock.tryLock("second-check:" + reportId)) check(reportId);
        });
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
