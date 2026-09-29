package com.soubhagya.systivex.twin.api;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Read shape for a SystemEntity. The JPA entity never leaves the service layer. */
public record SystemEntityResponse(
        UUID id,
        SystemEntityType type,
        String name,
        String externalRef,
        String environment,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static SystemEntityResponse from(SystemEntity entity) {
        return new SystemEntityResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getExternalRef(),
                entity.getEnvironment(),
                entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
