package com.truesight.history;

/** Whether a portfolio's earlier annual reports are being read. Kept in memory: what was read is kept in the database. */
public enum HistoryStatus {
    /** Nothing is being read. */
    IDLE,
    /** Earlier reports are being read now. */
    READING,
    /** Reading stopped on a failure; the reason is given and what was read is kept. */
    FAILED
}
