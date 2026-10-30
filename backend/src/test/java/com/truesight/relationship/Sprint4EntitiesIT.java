package com.truesight.relationship;

import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.company.ReportStatus;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Sprint 4 table changes against a real database, through the entities and repositories (the app starts with
 * {@code ddl-auto: validate}, so each entity must match its table).
 */
@IntegrationTest
class Sprint4EntitiesIT {

    private static final String EVIDENCE = "We rely on Example Supplier for all of our wafers.";

    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired PastRelationshipRepository pastRelationships;
    @Autowired RelationshipHighlightRepository highlights;
    @Autowired AiCallRepository aiCalls;

    private Company company(String name) {
        return companies.save(new Company(name + " " + UUID.randomUUID(), null, null));
    }

    private Report report(Company company) {
        return reports.save(new Report(company.getId(), "10-K", "s4-" + UUID.randomUUID().toString().substring(0, 20),
                LocalDate.of(2024, 2, 1), "https://example.com/report", "report text"));
    }

    private Relationship relationship() {
        Company buyer = company("Buyer");
        Company supplier = company("Supplier");
        Report report = report(buyer);
        return relationships.save(new Relationship(report.getId(), buyer.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "wafers", EVIDENCE));
    }

    @Test
    void aRelationshipFromAnOlderReportIsSavedAndReadBackByItsCompany() {
        Company buyer = company("Buyer");
        Company supplier = company("Supplier");
        Report olderReport = report(buyer);

        PastRelationship saved = pastRelationships.save(new PastRelationship(olderReport.getId(), buyer.getId(),
                supplier.getId(), RelationshipType.CUSTOMER, "wafers", EVIDENCE));

        List<PastRelationship> found = pastRelationships.findByCompanyIdIn(List.of(buyer.getId()));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(saved.getId());
        assertThat(found.get(0).getReportId()).isEqualTo(olderReport.getId());
        assertThat(found.get(0).getCounterpartyId()).isEqualTo(supplier.getId());
        assertThat(found.get(0).getType()).isEqualTo(RelationshipType.CUSTOMER);
        assertThat(found.get(0).getProvides()).isEqualTo("wafers");
        assertThat(found.get(0).getEvidence()).isEqualTo(EVIDENCE);
        assertThat(found.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void anOlderRelationshipNeverAppearsAmongTheCurrentRelationships() {
        Company buyer = company("Buyer");
        Company supplier = company("Supplier");
        pastRelationships.save(new PastRelationship(report(buyer).getId(), buyer.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, null, EVIDENCE));

        assertThat(relationships.findByCompanyIdIn(List.of(buyer.getId()))).isEmpty();
    }

    @Test
    void deletingAnOlderReportDeletesItsOlderRelationships() {
        Company buyer = company("Buyer");
        Company supplier = company("Supplier");
        Report olderReport = report(buyer);
        pastRelationships.save(new PastRelationship(olderReport.getId(), buyer.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, null, EVIDENCE));

        reports.deleteById(olderReport.getId());

        assertThat(pastRelationships.findByReportIdOrderByIdAsc(olderReport.getId())).isEmpty();
    }

    @Test
    void highlightsAreSavedAndReadBackInTheOrderTheyAppearInTheEvidence() {
        Relationship relationship = relationship();
        highlights.save(new RelationshipHighlight(relationship.getId(), HighlightKind.FIGURE, 30, 36));
        highlights.save(new RelationshipHighlight(relationship.getId(), HighlightKind.COMPANY, 11, 27));

        List<RelationshipHighlight> found = highlights.findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(
                List.of(relationship.getId()));

        assertThat(found).extracting(RelationshipHighlight::getKind)
                .containsExactly(HighlightKind.COMPANY, HighlightKind.FIGURE);
        assertThat(found).extracting(RelationshipHighlight::getStartAt).containsExactly(11, 30);
        assertThat(found).extracting(RelationshipHighlight::getEndAt).containsExactly(27, 36);
    }

    @Test
    void aHighlightThatEndsWhereItStartsOrEarlierIsRefused() {
        Relationship relationship = relationship();

        assertThatThrownBy(() -> highlights.saveAndFlush(
                new RelationshipHighlight(relationship.getId(), HighlightKind.COMPANY, 10, 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> highlights.saveAndFlush(
                new RelationshipHighlight(relationship.getId(), HighlightKind.COMPANY, -1, 5)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingARelationshipDeletesItsHighlights() {
        Relationship relationship = relationship();
        highlights.save(new RelationshipHighlight(relationship.getId(), HighlightKind.SUPPLIED, 40, 46));

        relationships.deleteById(relationship.getId());

        assertThat(highlights.findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(List.of(relationship.getId())))
                .isEmpty();
    }

    @Test
    void aRelationshipRemembersWhenItsEvidenceWasLastReadForHighlights() {
        Relationship relationship = relationship();
        assertThat(relationships.findById(relationship.getId()).orElseThrow().getHighlightedAt()).isNull();

        Instant readAt = Instant.parse("2026-10-29T01:00:00Z");
        relationship.setHighlightedAt(readAt);
        relationships.save(relationship);

        assertThat(relationships.findById(relationship.getId()).orElseThrow().getHighlightedAt()).isEqualTo(readAt);
    }

    @Test
    void aCompanyRemembersHowFarReadingItsOwnReportHasGot() {
        Company supplier = company("Supplier");
        assertThat(companies.findById(supplier.getId()).orElseThrow().getReportStatus()).isNull();

        supplier.setReportStatus(ReportStatus.FAILED);
        supplier.setReportStatusReason("The report could not be downloaded.");
        companies.save(supplier);

        Company found = companies.findById(supplier.getId()).orElseThrow();
        assertThat(found.getReportStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(found.getReportStatusReason()).isEqualTo("The report could not be downloaded.");
    }

    @Test
    void everyReportStatusCanBeSavedOnACompany() {
        for (ReportStatus status : ReportStatus.values()) {
            Company supplier = company("Supplier");
            supplier.setReportStatus(status);
            companies.save(supplier);

            assertThat(companies.findById(supplier.getId()).orElseThrow().getReportStatus()).isEqualTo(status);
        }
    }

    @Test
    void theAiCallRecordAcceptsAskTheTimelineAndTheHighlights() {
        for (AiPurpose purpose : List.of(AiPurpose.ASK, AiPurpose.TIMELINE, AiPurpose.HIGHLIGHT)) {
            AiCall saved = aiCalls.save(new AiCall(purpose, "test-model", "prompt", null, "answer",
                    AiOutcome.ANSWERED, null, Instant.parse("2035-05-05T01:00:00Z"), 5));

            assertThat(aiCalls.findById(saved.getId()).orElseThrow().getPurpose()).isEqualTo(purpose);
        }
    }
}
