package com.truesight.relationship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** When a company was last searched for by name in other companies' filings, and how many filings that gave. */
@Entity
@Table(name = "name_searches")
public class NameSearch {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;

    @Column(name = "filings_found", nullable = false)
    private int filingsFound;

    /** For JPA only. */
    protected NameSearch() {
    }

    public NameSearch(Long companyId, Instant searchedAt, int filingsFound) {
        this.companyId = companyId;
        this.searchedAt = searchedAt;
        this.filingsFound = filingsFound;
    }

    public Long getCompanyId() { return companyId; }
    public Instant getSearchedAt() { return searchedAt; }
    public int getFilingsFound() { return filingsFound; }

    public void searchedAgain(Instant searchedAt, int filingsFound) {
        this.searchedAt = searchedAt;
        this.filingsFound = filingsFound;
    }
}
