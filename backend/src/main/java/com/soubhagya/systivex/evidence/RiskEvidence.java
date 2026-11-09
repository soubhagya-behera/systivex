package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.RiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskEvidence(
        List<ImpactEvidence.ImpactStartEvidence> startedFrom,
        List<RiskEntryEvidence> risks,
        int maxDepth,
        String taxonomyVersion,
        Instant snapshotObservedAt,
        int entityCount,
        int relationshipCount,
        boolean truncated,
        String note) {

    public RiskEvidence {
        startedFrom = List.copyOf(startedFrom == null ? List.of() : startedFrom);
        risks = List.copyOf(risks == null ? List.of() : risks);
    }

    public record RiskEntryEvidence(
            UUID id,
            String type,
            String name,
            String externalRef,
            int depth,
            String viaStart,
            RiskLevel level,
            List<String> reasons,
            boolean uncertain,
            boolean stale,
            boolean truncated,
            int omittedReasonCount) {
        public RiskEntryEvidence {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
            if (omittedReasonCount < 0) {
                throw new IllegalArgumentException("Omitted reason count must not be negative");
            }
        }
    }
}
