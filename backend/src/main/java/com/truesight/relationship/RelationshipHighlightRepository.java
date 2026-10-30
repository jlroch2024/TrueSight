package com.truesight.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and saves the highlighted phrases of relationships' evidence. */
public interface RelationshipHighlightRepository extends JpaRepository<RelationshipHighlight, Long> {

    /** The highlights of these relationships, in the order they appear in the evidence. */
    List<RelationshipHighlight> findByRelationshipIdInOrderByRelationshipIdAscStartAtAsc(
            Collection<Long> relationshipIds);
}
