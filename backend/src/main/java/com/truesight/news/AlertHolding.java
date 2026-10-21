package com.truesight.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_holdings")
@IdClass(AlertHoldingId.class)
public class AlertHolding {
    @Id @Column(name = "alert_id", nullable = false)
    private Long alertId;
    @Id @Column(name = "holding_id", nullable = false)
    private Long holdingId;
    @Column(name = "relationship_id")
    private Long relationshipId;
    /** The second link, when the event reaches the holding through a supplier's supplier. Empty for one step. */
    @Column(name = "second_relationship_id")
    private Long secondRelationshipId;
    protected AlertHolding() {}
    public AlertHolding(Long alertId, Long holdingId, Long relationshipId) {
        this.alertId = alertId;
        this.holdingId = holdingId;
        this.relationshipId = relationshipId;
    }
    public Long getAlertId() { return alertId; }
    public Long getHoldingId() { return holdingId; }
    public Long getRelationshipId() { return relationshipId; }
    public Long getSecondRelationshipId() { return secondRelationshipId; }
    public void setSecondRelationshipId(Long secondRelationshipId) { this.secondRelationshipId = secondRelationshipId; }
}
