package com.truesight.relationship;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** When two relationships count as the two companies' reports saying the same thing. */
class AgreementTest {

    private static final long QUALCOMM = 1L;
    private static final long AMKOR = 2L;

    private final RelationshipRepository relationships = mock(RelationshipRepository.class);
    private final Agreement agreement = new Agreement(relationships);

    @Test
    void oneReportsSupplierAndTheOthersCustomerAgree() {
        Relationship amkorSuppliesQualcomm = saved(11L, 101L, QUALCOMM, AMKOR, RelationshipType.SUPPLIER);
        Relationship qualcommBuysFromAmkor = saved(12L, 102L, AMKOR, QUALCOMM, RelationshipType.CUSTOMER);
        stored(amkorSuppliesQualcomm, qualcommBuysFromAmkor);

        Map<Long, Relationship> found = agreement.of(List.of(amkorSuppliesQualcomm, qualcommBuysFromAmkor));

        assertThat(found).containsEntry(11L, qualcommBuysFromAmkor).containsEntry(12L, amkorSuppliesQualcomm);
    }

    @Test
    void theSameDirectionTwiceIsNoAgreement() {
        Relationship first = saved(11L, 101L, QUALCOMM, AMKOR, RelationshipType.SUPPLIER);
        Relationship second = saved(12L, 102L, AMKOR, QUALCOMM, RelationshipType.SUPPLIER);
        stored(first, second);

        assertThat(agreement.of(List.of(first, second))).isEmpty();
    }

    @Test
    void aRelationshipStatedByOneReportOnlyAgreesWithNothing() {
        Relationship alone = saved(11L, 101L, QUALCOMM, AMKOR, RelationshipType.SUPPLIER);
        stored(alone);

        assertThat(agreement.of(List.of(alone))).isEmpty();
    }

    @Test
    void aRejectedRelationshipNeverRaisesTheOther() {
        Relationship kept = saved(11L, 101L, QUALCOMM, AMKOR, RelationshipType.SUPPLIER);
        Relationship rejected = saved(12L, 102L, AMKOR, QUALCOMM, RelationshipType.CUSTOMER);
        rejected.setReviewStatus(ReviewStatus.REJECTED);
        stored(kept, rejected);

        assertThat(agreement.of(List.of(kept))).isEmpty();
    }

    @Test
    void anAnalystAddedRelationshipNeverCountsAsAReportNamingIt() {
        Relationship found = saved(11L, 101L, QUALCOMM, AMKOR, RelationshipType.SUPPLIER);
        Relationship added = saved(12L, 102L, AMKOR, QUALCOMM, RelationshipType.CUSTOMER);
        ReflectionTestUtils.setField(added, "source", RelationshipSource.ANALYST);
        stored(found, added);

        assertThat(agreement.of(List.of(found, added))).isEmpty();
    }

    private void stored(Relationship... all) {
        when(relationships.findByCompanyIdInAndCounterpartyIdIn(anyCollection(), anyCollection())).thenReturn(List.of(all));
    }

    private static Relationship saved(long id, long reportId, long company, long counterparty, RelationshipType type) {
        Relationship relationship = new Relationship(reportId, company, counterparty, type, "packaging", "Set by the test.");
        ReflectionTestUtils.setField(relationship, "id", id);
        return relationship;
    }
}
