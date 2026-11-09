package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.SimulationOperation;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record EvidencePacketRequest(
        EvidenceMode mode,
        List<String> from,
        Integer maxDepth,
        List<String> includeTypes,
        List<SimulationOperation> operations,
        UUID proposalKey,
        Integer revisionNumber,
        boolean includeAuditEvidence) {

    public EvidencePacketRequest {
        if (mode == null) {
            throw new IllegalArgumentException(
                    "mode must not be null; exactly one EvidenceMode is required");
        }
        if (from == null || from.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one starting entity is required (field 'from')");
        }
        List<String> cleaned = new ArrayList<>();
        for (String raw : from) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "Starting entity identifiers must not be blank");
            }
            cleaned.add(raw.trim());
        }
        if (cleaned.size() > EvidenceLimits.MAX_ROOTS) {
            throw new IllegalArgumentException(
                    "At most " + EvidenceLimits.MAX_ROOTS + " starting entities are supported");
        }
        if (new HashSet<>(cleaned).size() != cleaned.size()) {
            throw new IllegalArgumentException("Duplicate starting entities are not allowed");
        }
        from = List.copyOf(cleaned);
        if (maxDepth != null
                && (maxDepth < EvidenceLimits.MIN_DEPTH || maxDepth > EvidenceLimits.MAX_DEPTH)) {
            throw new IllegalArgumentException(
                    "maxDepth must be between "
                            + EvidenceLimits.MIN_DEPTH + " and " + EvidenceLimits.MAX_DEPTH);
        }
        if (includeTypes == null) {
            includeTypes = List.of();
        } else {
            List<String> types = new ArrayList<>();
            for (String raw : includeTypes) {
                if (raw == null || raw.isBlank()) {
                    throw new IllegalArgumentException("includeTypes values must not be blank");
                }
                try {
                    SystemEntityType.valueOf(raw.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown entity type: " + raw.trim());
                }
                types.add(raw.trim());
            }
            includeTypes = List.copyOf(types);
        }
        if (operations == null) {
            operations = List.of();
        } else {
            for (SimulationOperation operation : operations) {
                if (operation == null) {
                    throw new IllegalArgumentException("Operation type is required");
                }
            }
            operations = List.copyOf(operations);
        }
        if (mode == EvidenceMode.BASELINE && !operations.isEmpty()) {
            throw new IllegalArgumentException(
                    "operations are only allowed in SIMULATION or POLICY_EVALUATION mode");
        }
        if (operations.size() > EvidenceLimits.MAX_SIMULATION_OPERATIONS) {
            throw new IllegalArgumentException(
                    "At most " + EvidenceLimits.MAX_SIMULATION_OPERATIONS
                            + " simulation operations are supported");
        }
        if (revisionNumber != null) {
            if (proposalKey == null) {
                throw new IllegalArgumentException("revisionNumber requires proposalKey");
            }
            if (revisionNumber < 1) {
                throw new IllegalArgumentException("revisionNumber must be positive");
            }
        }
        if (includeAuditEvidence && proposalKey == null) {
            throw new IllegalArgumentException("includeAuditEvidence requires proposalKey");
        }
    }
}
