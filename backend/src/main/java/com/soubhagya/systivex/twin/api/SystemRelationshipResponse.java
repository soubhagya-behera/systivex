package com.soubhagya.systivex.twin.api;

import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.UUID;

/** Read shape for a SystemRelationship. */
public record SystemRelationshipResponse(
        UUID id,
        UUID sourceId,
        UUID targetId,
        SystemRelationshipType type,
        Instant createdAt) {

    public static SystemRelationshipResponse from(SystemRelationship relationship) {
        return new SystemRelationshipResponse(
                relationship.getId(),
                relationship.getSource().getId(),
                relationship.getTarget().getId(),
                relationship.getRelationshipType(),
                relationship.getCreatedAt());
    }
}
