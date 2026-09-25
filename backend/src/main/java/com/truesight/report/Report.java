package com.truesight.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A company's annual report, saved as plain text. One row of the {@code reports} table.
 *
 * <p>{@code form} is "10-K" for a US company or "20-F" for a foreign one. {@code accessionNumber} is the SEC's own
 * number for the filing, which is how the same report is never downloaded twice. {@code url} opens the original on
 * the SEC website.
 *
 * <p>A report belongs to the company, not to any portfolio: reports are public, so one download serves everybody.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String form;

    @Column(name = "accession_number", nullable = false, unique = true)
    private String accessionNumber;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(nullable = false)
    private String url;

    /** The whole report as plain text, one paragraph per line. */
    @Column(nullable = false)
    private String text;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Report() {
    }

    public Report(Long companyId, String form, String accessionNumber, LocalDate filingDate, String url, String text) {
        this.companyId = companyId;
        this.form = form;
        this.accessionNumber = accessionNumber;
        this.filingDate = filingDate;
        this.url = url;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getForm() {
        return form;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
