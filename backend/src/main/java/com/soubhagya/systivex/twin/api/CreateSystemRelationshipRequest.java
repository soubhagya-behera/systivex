package com.soubhagya.systivex.twin.api;

import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Validated payload for creating a directed relationship between two entities. */
public record CreateSystemRelationshipRequest(
        @NotNull UUID sourceId,
        @NotNull UUID targetId,
        @NotNull SystemRelationshipType type) {}
