package com.truesight.relationship;

/**
 * A company whose annual report can support a relationship an analyst adds. {@code name} is as stored;
 * {@code displayName} is how every page shows it (see {@link com.truesight.company.CompanyDisplayNames}).
 */
public record CompanyOption(Long id, String name, String displayName) {
}
