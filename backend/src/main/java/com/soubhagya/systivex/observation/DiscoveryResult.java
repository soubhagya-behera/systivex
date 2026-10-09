package com.soubhagya.systivex.observation;

import java.util.List;

/**
 * Complete, pre-validated discovery result. The scanner either returns a
 * fully consistent graph or throws; it never returns a partial one, so the
 * sync service can persist the whole result in a single transaction.
 */
public record DiscoveryResult(List<DiscoveredEntity> entities, List<DiscoveredRelationship> relationships) {

    public DiscoveryResult {
        entities = List.copyOf(entities);
        relationships = List.copyOf(relationships);
    }
}
