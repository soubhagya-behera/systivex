package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.time.Instant;
import java.util.UUID;

public record TwinEntityEvidence(
        UUID id,
        SystemEntityType type,
        String name,
        String externalRef,
        String environment,
        Instant createdAt,
        Instant updatedAt) {
}
