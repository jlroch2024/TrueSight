package com.truesight.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One call to Gemini, for the AI Audit Trail: why it was made, what was asked, what came back and how long it took.
 *
 * <p>A report's text is never stored here. The prompt keeps its place with a marker, such as
 * {@code [report 12: 90628 characters]}, and {@code reportId} names the report.
 */
@Entity
@Table(name = "ai_calls")
public class AiCall {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private AiPurpose purpose;
    @Column(nullable = false, length = 100)
    private String model;
    @Column(nullable = false)
    private String prompt;
    @Column(name = "report_id")
    private Long reportId;
    /** Gemini's answer exactly as it arrived; empty when there was none. */
    private String response;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AiOutcome outcome;
    /** Why the call failed, in the words shown to the user. Never contains the API key. */
    @Column(length = 500)
    private String error;
    /** For finding relationships: how many quotes were found in the report and kept, and how many were dropped. */
    private Integer kept;
    private Integer dropped;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    protected AiCall() {}

    public AiCall(AiPurpose purpose, String model, String prompt, Long reportId, String response, AiOutcome outcome,
                  String error, Instant startedAt, int durationMs) {
        this.purpose = purpose;
        this.model = model;
        this.prompt = prompt;
        this.reportId = reportId;
        this.response = response;
        this.outcome = outcome;
        this.error = error;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public AiPurpose getPurpose() { return purpose; }
    public String getModel() { return model; }
    public String getPrompt() { return prompt; }
    public Long getReportId() { return reportId; }
    public String getResponse() { return response; }
    public AiOutcome getOutcome() { return outcome; }
    public String getError() { return error; }
    public Integer getKept() { return kept; }
    public Integer getDropped() { return dropped; }
    public Instant getStartedAt() { return startedAt; }
    public Integer getDurationMs() { return durationMs; }
    public void setOutcome(AiOutcome outcome) { this.outcome = outcome; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public void setKept(Integer kept) { this.kept = kept; }
    public void setDropped(Integer dropped) { this.dropped = dropped; }
}
