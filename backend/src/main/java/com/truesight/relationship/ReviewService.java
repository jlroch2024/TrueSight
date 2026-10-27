package com.truesight.relationship;

import com.truesight.common.ApiException;
import com.truesight.user.CurrentUser;
import com.truesight.user.User;
import com.truesight.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Analyst's review of the relationships the AI found. Relationships are shared, so one decision applies to every
 * portfolio manager. Callers have already checked the user is an Analyst ({@code CurrentUser#requireAnalyst}).
 */
@Service
public class ReviewService {

    private final RelationshipRepository relationships;
    private final RelationshipService relationshipService;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public ReviewService(RelationshipRepository relationships, RelationshipService relationshipService,
                         UserRepository users, CurrentUser currentUser) {
        this.relationships = relationships;
        this.relationshipService = relationshipService;
        this.users = users;
        this.currentUser = currentUser;
    }

    /**
     * The relationships with this review status, or all of them when {@code status} is null. Unreviewed ones come
     * first, and among them the ones the second AI doubts; then the ones more portfolios rely on; then the newest
     * report.
     */
    @Transactional(readOnly = true)
    public List<ReviewItemResponse> list(ReviewStatus status) {
        List<Relationship> found = status == null ? relationships.findAll() : relationships.findByReviewStatus(status);
        return toItems(found).stream()
                .sorted(Comparator
                        .comparing((ReviewItemResponse item) -> item.reviewStatus() != ReviewStatus.UNREVIEWED)
                        .thenComparing((ReviewItemResponse item) -> !(item.reviewStatus() == ReviewStatus.UNREVIEWED
                                && item.secondCheck() == SecondCheckVerdict.DOUBTS))
                        .thenComparing(ReviewItemResponse::usedByPortfolios, Comparator.reverseOrder())
                        .thenComparing((ReviewItemResponse item) -> item.relationship().report().filingDate(),
                                Comparator.reverseOrder())
                        .thenComparing((ReviewItemResponse item) -> item.relationship().id()))
                .toList();
    }

    /** Records the decision, who made it and when. The latest decision counts, so a decision can be changed. */
    @Transactional
    public ReviewItemResponse review(Long relationshipId, ReviewRequest request) {
        Relationship relationship = relationships.findById(relationshipId)
                .orElseThrow(() -> ApiException.notFound("Relationship not found."));
        if (request.decision() == ReviewStatus.UNREVIEWED) {
            throw ApiException.badRequest("Please approve or reject this relationship.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (request.decision() == ReviewStatus.REJECTED && note == null) {
            throw ApiException.badRequest("Please say why this is rejected.");
        }
        relationship.setReviewStatus(request.decision());
        relationship.setReviewNote(note);
        relationship.setReviewedBy(currentUser.id());
        relationship.setReviewedAt(Instant.now());
        relationships.save(relationship);
        return toItems(List.of(relationship)).get(0);
    }

    List<ReviewItemResponse> toItems(List<Relationship> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<RelationshipResponse> responses = relationshipService.toResponses(found);
        Map<Long, Long> usedBy = new HashMap<>();
        for (Object[] row : relationships.countPortfoliosUsingEach()) {
            usedBy.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        // The Analyst's display name once they have set one, otherwise their email.
        Map<Long, String> reviewerName = users.findAllById(found.stream()
                        .map(Relationship::getReviewedBy).filter(id -> id != null).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::nameShown, (a, b) -> a));
        Map<Long, Relationship> byId = found.stream().collect(Collectors.toMap(Relationship::getId, Function.identity()));

        return responses.stream().map(response -> {
            Relationship relationship = byId.get(response.id());
            return new ReviewItemResponse(
                    response,
                    relationship.getReviewStatus(),
                    relationship.getReviewNote(),
                    reviewerName.get(relationship.getReviewedBy()),
                    relationship.getReviewedAt(),
                    usedBy.getOrDefault(relationship.getId(), 0L),
                    relationship.getSecondCheck(),
                    relationship.getSecondCheckReason());
        }).toList();
    }
}
