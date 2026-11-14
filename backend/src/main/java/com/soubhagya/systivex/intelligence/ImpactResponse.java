package com.soubhagya.systivex.intelligence;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read shape of one deterministic change-impact analysis run.
 *
 * <p>Every impacted entity carries at least one complete evidence path in
 * {@code paths}: ordered steps over persisted relationships, each recording
 * the traversal direction actually taken. Starting entities are listed
 * separately in {@code startedFrom} (depth zero) and never counted as
 * impacted. Results are "potentially affected" components, not confirmed
 * breakage — see {@code note}.
 *
 * <p>{@code nodeCount} is the number of nodes the traversal actually
 * covered: the starting entities plus every reached node that owns a
 * recorded evidence path. A node refused by the node cap or the path cap was
 * never traversed and is therefore not counted, so {@code nodeCount} never
 * exceeds {@code limits.maxNodes}. It can exceed
 * {@code startedFrom().size() + impacted().size()}, because the traversal
 * passes through nodes that the type filter excludes from the report.
 */
public record ImpactResponse(
        List<StartEntity> startedFrom,
        List<ImpactedEntity> impacted,
        List<ImpactPath> paths,
        int maxDepth,
        int nodeCount,
        int pathCount,
        boolean truncated,
        String truncationReason,
        boolean cycleEncountered,
        Instant snapshotObservedAt,
        int entityCount,
        int relationshipCount,
        Limits limits,
        String note) {

    public record StartEntity(
            UUID id,
            SystemEntityType type,
            String name,
            String externalRef,
            String environment) {}

    public record ImpactedEntity(
            UUID id,
            SystemEntityType type,
            String name,
            String externalRef,
            int depth,
            String viaStart) {}

    public record ImpactPath(
            UUID targetId,
            String targetExternalRef,
            int depth,
            List<ImpactStep> steps,
            String explanation) {}

    public record ImpactStep(
            UUID sourceId,
            String sourceExternalRef,
            UUID targetId,
            String targetExternalRef,
            SystemRelationshipType type,
            ImpactDirection direction) {}

    public record Limits(int maxStarts, int maxDepthAllowed, int maxNodes, int maxPaths) {}
}
