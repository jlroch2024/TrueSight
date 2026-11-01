package com.truesight.news;

import com.truesight.portfolio.Holding;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that decides which holdings a company's trouble reaches, shared by alerts (one step either way) and the
 * Disruption Simulator (downstream only). No database: companies are just numbers.
 */
class ReachTest {

    private static final long SUPPLIER = 10;
    private static final long MIDDLE = 20;
    private static final long CUSTOMER = 30;
    private static final long HOLDING = 40;

    private long nextId = 1;

    @Test
    void aCompanyThatIsAHoldingReachesItselfWithNoRelationshipInBothDirections() {
        Holding held = holding(1, HOLDING);

        for (Reach.Direction direction : Reach.Direction.values()) {
            Map<Holding, Reach.Path> reached = Reach.reached(HOLDING, List.of(held), List.of(), direction);

            assertThat(reached).containsOnlyKeys(held);
            assertThat(reached.get(held)).isEqualTo(new Reach.Path(null, null));
        }
    }

    @Test
    void aSupplierReachesTheHoldingItSuppliesInBothDirections() {
        Holding held = holding(1, HOLDING);
        Relationship supplies = suppliesToHolding(SUPPLIER, HOLDING);

        for (Reach.Direction direction : Reach.Direction.values()) {
            Map<Holding, Reach.Path> reached = Reach.reached(SUPPLIER, List.of(held), List.of(supplies), direction);

            assertThat(reached.get(held)).isEqualTo(new Reach.Path(supplies.getId(), null));
        }
    }

    @Test
    void aSupplierReachesAHoldingSuppliedByACompanyItSuppliesInTwoSteps() {
        Holding held = holding(1, HOLDING);
        Relationship first = suppliesToHolding(SUPPLIER, MIDDLE);
        Relationship second = suppliesToHolding(MIDDLE, HOLDING);

        for (Reach.Direction direction : Reach.Direction.values()) {
            Map<Holding, Reach.Path> reached = Reach.reached(SUPPLIER, List.of(held), List.of(first, second),
                    direction);

            assertThat(reached.get(held)).isEqualTo(new Reach.Path(first.getId(), second.getId()));
        }
    }

    @Test
    void aHoldingThatOnlySuppliesTheCompanyIsReachedByAnEventButNotByTheCompanyStopping() {
        Holding held = holding(1, HOLDING);
        // The holding's own report says the company buys from it.
        Relationship customer = relationship(HOLDING, CUSTOMER, RelationshipType.CUSTOMER, ReviewStatus.UNREVIEWED);

        Map<Holding, Reach.Path> eitherWay = Reach.reached(CUSTOMER, List.of(held), List.of(customer),
                Reach.Direction.EITHER_WAY);
        Map<Holding, Reach.Path> downstream = Reach.reached(CUSTOMER, List.of(held), List.of(customer),
                Reach.Direction.DOWNSTREAM);

        assertThat(eitherWay.get(held)).isEqualTo(new Reach.Path(customer.getId(), null));
        assertThat(downstream).isEmpty();
    }

    @Test
    void aHoldingThatSuppliesTheCompanyOnlyThroughAMiddleCompanyIsNotReachedDownstream() {
        Holding held = holding(1, HOLDING);
        Relationship holdingSuppliesMiddle = suppliesToHolding(HOLDING, MIDDLE);
        Relationship middleSuppliesCompany = suppliesToHolding(MIDDLE, SUPPLIER);

        Map<Holding, Reach.Path> reached = Reach.reached(SUPPLIER, List.of(held),
                List.of(holdingSuppliesMiddle, middleSuppliesCompany), Reach.Direction.DOWNSTREAM);

        assertThat(reached).isEmpty();
    }

    @Test
    void aSupplierSeveralStepsAwayReachesNothing() {
        Holding held = holding(1, HOLDING);
        Relationship first = suppliesToHolding(SUPPLIER, MIDDLE);
        Relationship second = suppliesToHolding(MIDDLE, CUSTOMER);
        Relationship third = suppliesToHolding(CUSTOMER, HOLDING);

        for (Reach.Direction direction : Reach.Direction.values()) {
            assertThat(Reach.reached(SUPPLIER, List.of(held), List.of(first, second, third), direction)).isEmpty();
        }
    }

    @Test
    void whenSeveralRelationshipsJoinTheSamePairTheApprovedOneIsKeptThenTheLowestId() {
        Holding held = holding(1, HOLDING);
        Relationship lowestUnreviewed = relationship(HOLDING, SUPPLIER, RelationshipType.SUPPLIER,
                ReviewStatus.UNREVIEWED);
        Relationship laterApproved = relationship(HOLDING, SUPPLIER, RelationshipType.SUPPLIER,
                ReviewStatus.APPROVED);
        Relationship evenLaterApproved = relationship(HOLDING, SUPPLIER, RelationshipType.SUPPLIER,
                ReviewStatus.APPROVED);

        for (Reach.Direction direction : Reach.Direction.values()) {
            Map<Holding, Reach.Path> reached = Reach.reached(SUPPLIER, List.of(held),
                    List.of(lowestUnreviewed, evenLaterApproved, laterApproved), direction);

            assertThat(reached.get(held).relationshipId()).isEqualTo(laterApproved.getId());
        }
    }

    @Test
    void everyHoldingReachedIsKeptInTheOrderOfTheHoldingsGiven() {
        Holding first = holding(1, HOLDING);
        Holding second = holding(2, HOLDING + 1);
        Relationship toFirst = suppliesToHolding(SUPPLIER, HOLDING);
        Relationship toSecond = suppliesToHolding(SUPPLIER, HOLDING + 1);

        Map<Holding, Reach.Path> reached = Reach.reached(SUPPLIER, List.of(first, second),
                List.of(toSecond, toFirst), Reach.Direction.DOWNSTREAM);

        assertThat(reached.keySet()).containsExactly(first, second);
    }

    private Holding holding(long id, long company) {
        Holding holding = new Holding(1L, "T" + id, BigDecimal.TEN);
        holding.setCompanyId(company);
        ReflectionTestUtils.setField(holding, "id", id);
        return holding;
    }

    /** "{@code supplier} supplies {@code customer}", as the customer's own report says it. */
    private Relationship suppliesToHolding(long supplier, long customer) {
        return relationship(customer, supplier, RelationshipType.SUPPLIER, ReviewStatus.UNREVIEWED);
    }

    private Relationship relationship(long company, long counterparty, RelationshipType type, ReviewStatus status) {
        Relationship relationship = new Relationship(1L, company, counterparty, type, null, "Evidence.");
        relationship.setReviewStatus(status);
        ReflectionTestUtils.setField(relationship, "id", nextId++);
        return relationship;
    }
}
