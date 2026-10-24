package com.truesight.relationship;

import com.truesight.ai.AiCallLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Applies verified criticality in a short transaction after the external Gemini request has finished. */
@Service
public class CriticalityResultSaver {

    private final RelationshipRepository relationships;
    private final AiCallLog calls;

    public CriticalityResultSaver(RelationshipRepository relationships, AiCallLog calls) {
        this.relationships = relationships;
        this.calls = calls;
    }

    @Transactional
    public void save(Long reportId, Long callId, Map<Long, VerifiedCriticality> verified, int dropped) {
        for (Relationship relationship : relationships.findByReportIdOrderByIdAsc(reportId)) {
            VerifiedCriticality value = verified.get(relationship.getId());
            if (relationship.getType() == RelationshipType.SUPPLIER && relationship.getCriticality() == null
                    && value != null) {
                relationship.setCriticality(value.criticality());
                relationship.setCriticalityEvidence(value.evidence());
            }
        }
        calls.finish(callId, verified.size(), dropped);
    }

    public record VerifiedCriticality(Criticality criticality, String evidence) { }
}
