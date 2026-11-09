package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImpactEvidence(
        List<ImpactStartEvidence> startedFrom,
        List<ImpactedEntityEvidence> impacted,
        List<ImpactPathEvidence> paths,
        int maxDepth,
        int nodeCount,
        int pathCount,
        boolean truncated,
        String truncationReason,
        boolean cycleEncountered,
        Instant snapshotObservedAt,
        int entityCount,
        int relationshipCount,
        ImpactLimitsEvidence limits,
        String note) {

    public ImpactEvidence {
        startedFrom = List.copyOf(startedFrom == null ? List.of() : startedFrom);
        impacted = List.copyOf(impacted == null ? List.of() : impacted);
        paths = List.copyOf(paths == null ? List.of() : paths);
    }

    public record ImpactStartEvidence(
            UUID id,
            SystemEntityType type,
            String name,
            String externalRef,
            String environment) {
    }

    public record ImpactedEntityEvidence(
            UUID id,
            SystemEntityType type,
            String name,
            String externalRef,
            int depth,
            String viaStart) {
    }

    public record ImpactPathEvidence(
            UUID targetId,
            String targetExternalRef,
            int depth,
            List<ImpactStepEvidence> steps,
            String explanation) {
        public ImpactPathEvidence {
            steps = List.copyOf(steps == null ? List.of() : steps);
        }
    }

    public record ImpactStepEvidence(
            UUID sourceId,
            String sourceExternalRef,
            UUID targetId,
            String targetExternalRef,
            SystemRelationshipType type,
            String direction) {
    }

    public record ImpactLimitsEvidence(
            int maxStarts,
            int maxDepthAllowed,
            int maxNodes,
            int maxPaths) {
    }
}
