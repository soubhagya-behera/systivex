package com.soubhagya.systivex.observation;

import com.soubhagya.systivex.twin.model.SystemRelationshipType;

/**
 * One directed twin edge discovered from the repository, addressed by the
 * stable {@code externalRef} values of its endpoints (resolved to database
 * rows only inside the sync transaction).
 */
public record DiscoveredRelationship(
        String sourceRef, String targetRef, SystemRelationshipType type) {}
