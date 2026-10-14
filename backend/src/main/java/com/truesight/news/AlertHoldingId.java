package com.truesight.news;

import java.io.Serializable;
import java.util.Objects;

public class AlertHoldingId implements Serializable {
    private Long alertId;
    private Long holdingId;
    public AlertHoldingId() {}
    public AlertHoldingId(Long alertId, Long holdingId) { this.alertId = alertId; this.holdingId = holdingId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AlertHoldingId that)) return false;
        return Objects.equals(alertId, that.alertId) && Objects.equals(holdingId, that.holdingId);
    }
    @Override public int hashCode() { return Objects.hash(alertId, holdingId); }
}
