package com.soubhagya.systivex.observation;

import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.util.Map;

/**
 * One twin entity discovered from the target-service repository. The scanner
 * builds these from files it reads; {@link TwinSyncService} persists them.
 */
public record DiscoveredEntity(
        SystemEntityType type,
        String name,
        String externalRef,
        String environment,
        Map<String, Object> metadata) {}
