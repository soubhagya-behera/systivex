package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.UUID;

public record TwinRelationshipEvidence(
        UUID id,
        UUID sourceId,
        UUID targetId,
        SystemRelationshipType type,
        Instant createdAt) {
}
