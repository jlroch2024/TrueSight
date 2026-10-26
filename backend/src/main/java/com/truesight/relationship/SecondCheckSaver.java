package com.truesight.relationship;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Saves the second check's verdicts in a short transaction, after the Gemini request has finished. */
@Service
public class SecondCheckSaver {

    private final RelationshipRepository relationships;
    private final SecondCheck secondCheck;

    public SecondCheckSaver(RelationshipRepository relationships, SecondCheck secondCheck) {
        this.relationships = relationships;
        this.secondCheck = secondCheck;
    }

    /** Records each verdict on its relationship, unless something else has checked that relationship meanwhile. */
    @Transactional
    public void save(Map<Long, SecondCheck.Result> results) {
        for (Relationship relationship : relationships.findAllById(results.keySet())) {
            if (relationship.getSecondCheck() == null) {
                secondCheck.apply(relationship, results.get(relationship.getId()));
            }
        }
    }
}
