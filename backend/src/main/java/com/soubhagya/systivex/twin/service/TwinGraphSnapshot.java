package com.soubhagya.systivex.twin.service;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import java.util.List;

/**
 * Immutable, bounded snapshot of the persisted twin graph.
 *
 * <p>The lists contain only the accepted graph. Counts are exact when the
 * snapshot is within bounds and are lower bounds when overflow is detected.
 * {@code availableForAnalysis} is false whenever the snapshot is truncated or
 * internally inconsistent.
 */
public record TwinGraphSnapshot(
        List<SystemEntity> entities,
        List<SystemRelationship> relationships,
        int entityCount,
        int relationshipCount,
        boolean truncated,
        boolean availableForAnalysis,
        boolean consistent,
        List<String> limitations) {

    public TwinGraphSnapshot {
        entities = List.copyOf(entities);
        relationships = List.copyOf(relationships);
        limitations = List.copyOf(limitations);
    }

    public boolean entityOverflow() {
        return truncated && entityCount > entities.size();
    }

    public boolean relationshipOverflow() {
        return truncated && relationshipCount > relationships.size();
    }

    public int entityCountAtLeast() {
        return entityOverflow() ? entityCount : 0;
    }

    public int relationshipCountAtLeast() {
        return relationshipOverflow() ? relationshipCount : 0;
    }
}
