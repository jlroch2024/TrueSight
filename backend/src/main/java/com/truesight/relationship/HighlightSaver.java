package com.truesight.relationship;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Saves the highlights in a short transaction, after the Gemini request has finished. */
@Service
public class HighlightSaver {

    private final RelationshipRepository relationships;
    private final RelationshipHighlightRepository highlights;

    public HighlightSaver(RelationshipRepository relationships, RelationshipHighlightRepository highlights) {
        this.relationships = relationships;
        this.highlights = highlights;
    }

    /**
     * Saves each relationship's highlights and marks its quote as read, even with none found, so it is not read again. A
     * quote something else has read meanwhile keeps what it has.
     */
    @Transactional
    public void save(Map<Long, List<RelationshipHighlight>> found) {
        Instant now = Instant.now();
        for (Relationship relationship : relationships.findAllById(found.keySet())) {
            if (relationship.getHighlightedAt() == null) {
                highlights.saveAll(found.get(relationship.getId()));
                relationship.setHighlightedAt(now);
            }
        }
    }
}
