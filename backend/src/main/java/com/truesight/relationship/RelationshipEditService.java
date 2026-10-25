package com.truesight.relationship;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Analyst corrections and additions to the shared relationships found in annual reports. */
@Service
public class RelationshipEditService {

    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final CompanyDirectory companyDirectory;
    private final CompanyDisplayNames displayNames;
    private final ReportRepository reports;
    private final ReviewService reviews;
    private final CurrentUser currentUser;

    public RelationshipEditService(RelationshipRepository relationships, CompanyRepository companies,
                                   CompanyDirectory companyDirectory, CompanyDisplayNames displayNames,
                                   ReportRepository reports, ReviewService reviews, CurrentUser currentUser) {
        this.relationships = relationships;
        this.companies = companies;
        this.companyDirectory = companyDirectory;
        this.displayNames = displayNames;
        this.reports = reports;
        this.reviews = reviews;
        this.currentUser = currentUser;
    }

    /** Changes only what the relationship provides; its report evidence remains untouched. */
    @Transactional
    public ReviewItemResponse edit(Long relationshipId, EditRelationshipRequest request) {
        Relationship relationship = relationships.findById(relationshipId)
                .orElseThrow(() -> ApiException.notFound("Relationship not found."));
        relationship.setProvides(optionalText(request.provides()));
        relationship.setEditedBy(currentUser.id());
        relationship.setEditedAt(Instant.now());
        relationships.save(relationship);
        return reviews.toItems(List.of(relationship)).get(0);
    }

    /** Checks an analyst's sentence against the latest report, then saves its matching report passage as evidence. */
    @Transactional
    public ReviewItemResponse add(AddRelationshipRequest request) {
        Company company = companies.findById(request.companyId())
                .orElseThrow(() -> ApiException.notFound("Company not found."));
        Report report = reports.findFirstByCompanyIdOrderByFilingDateDesc(company.getId())
                .orElseThrow(() -> sentenceNotFound(company));
        String evidence = new QuoteChecker(report.getText()).evidenceFor(request.sentence())
                .orElseThrow(() -> sentenceNotFound(company));

        Company counterparty = companyDirectory.findOrCreate(
                request.counterpartyName().strip(), null, null, List.of());
        if (company.getId().equals(counterparty.getId())) {
            throw ApiException.badRequest("A company cannot have a relationship with itself.");
        }
        if (relationships.existsByCompanyIdAndCounterpartyIdAndTypeAndEvidence(
                company.getId(), counterparty.getId(), request.type(), evidence)) {
            throw ApiException.conflict("That relationship is already recorded.");
        }

        Instant now = Instant.now();
        Relationship relationship = new Relationship(report.getId(), company.getId(), counterparty.getId(),
                request.type(), optionalText(request.provides()), evidence);
        relationship.setSource(RelationshipSource.ANALYST);
        relationship.setAddedBy(currentUser.id());
        relationship.setReviewStatus(ReviewStatus.APPROVED);
        relationship.setReviewedBy(currentUser.id());
        relationship.setReviewedAt(now);
        relationships.save(relationship);
        return reviews.toItems(List.of(relationship)).get(0);
    }

    /** Companies with at least one report, for the Add Relationship form. */
    @Transactional(readOnly = true)
    public List<CompanyOption> companiesWithReport() {
        return companies.findAllById(reports.findCompanyIdsWithAReport()).stream()
                .map(company -> new CompanyOption(company.getId(), company.getName(), displayNames.of(company)))
                .sorted(Comparator.comparing(CompanyOption::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private ApiException sentenceNotFound(Company company) {
        return ApiException.badRequest("That sentence is not in " + displayNames.of(company) + "'s annual report.");
    }
}
