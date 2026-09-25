package com.truesight.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One name a company has been seen under, e.g. "TSMC" or "Taiwan Semiconductor Manufacturing Company Limited".
 * One row of the {@code company_names} table.
 *
 * <p>{@code name} is exactly as written. {@code nameKey} is the same name tidied for comparison (capitals,
 * punctuation and endings such as "Inc." removed). Each tidied name belongs to one company, which is how the same
 * company is recognised however a report writes it.
 */
@Entity
@Table(name = "company_names")
public class CompanyName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true)
    private String nameKey;

    /** For JPA only. */
    protected CompanyName() {
    }

    public CompanyName(Long companyId, String name, String nameKey) {
        this.companyId = companyId;
        this.name = name;
        this.nameKey = nameKey;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }
}
