package com.soubhagya.systivex.twin.api;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Validated payload for creating a SystemEntity. */
public record CreateSystemEntityRequest(
        @NotNull SystemEntityType type,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 512) String externalRef,
        @Size(max = 64) String environment,
        Map<String, Object> metadata) {}
