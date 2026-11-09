package com.soubhagya.systivex.evidence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record EvidencePacket(
        EvidenceReference packetRef,
        EvidenceMode mode,
        List<EvidenceItem<?>> items,
        EvidencePacketLimits limits,
        List<EvidenceOmission> omissions) {

    public EvidencePacket {
        if (packetRef == null) {
            throw new IllegalArgumentException("Packet reference must not be null");
        }
        if (!packetRef.value().startsWith("P")) {
            throw new IllegalArgumentException("Packet reference must use the P<n> form");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Packet mode must not be null");
        }
        if (limits == null) {
            throw new IllegalArgumentException("Packet limits must not be null");
        }
        items = List.copyOf(items == null ? List.of() : items);
        omissions = List.copyOf(omissions == null ? List.of() : omissions);
        if (items.size() > limits.maxTotalItems()) {
            throw new IllegalArgumentException(
                    "Evidence packet exceeds the total item limit of " + limits.maxTotalItems());
        }
        Set<String> references = new HashSet<>();
        Map<String, Integer> perSource = new HashMap<>();
        for (EvidenceItem<?> item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Evidence packet items must not be null");
            }
            if (!item.ref().value().startsWith("E")) {
                throw new IllegalArgumentException(
                        "Evidence item reference must use the E<n> form: " + item.ref().value());
            }
            if (!references.add(item.ref().value())) {
                throw new IllegalArgumentException(
                        "Duplicate evidence reference in packet: " + item.ref().value());
            }
            int count = perSource.merge(item.source(), 1, Integer::sum);
            if (count > limits.maxItemsPerSourceFamily()) {
                throw new IllegalArgumentException(
                        "Evidence source family exceeds its item limit: " + item.source());
            }
        }
    }

    public record EvidencePacketLimits(
            int maxRoots,
            int minDepth,
            int maxDepth,
            int defaultDepth,
            int maxImpactNodes,
            int maxImpactPaths,
            int maxItemsPerSourceFamily,
            int maxTotalItems,
            int maxSerializedBytes,
            int maxAuditEvents,
            int maxTwinEntities,
            int maxTwinRelationships,
            int maxImpactedEntities,
            int maxImpactPathsPacket,
            int maxRiskEntries,
            int maxPolicyRules,
            int maxDeltaStrings,
            int maxDeltaStringLength,
            int maxRiskReasons,
            int maxTextLength,
            int maxLimitations,
            int maxProposalOperations) {

        public static EvidencePacketLimits defaults() {
            return new EvidencePacketLimits(
                    EvidenceLimits.MAX_ROOTS,
                    EvidenceLimits.MIN_DEPTH,
                    EvidenceLimits.MAX_DEPTH,
                    EvidenceLimits.DEFAULT_DEPTH,
                    EvidenceLimits.MAX_IMPACT_NODES,
                    EvidenceLimits.MAX_IMPACT_PATHS,
                    EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY,
                    EvidenceLimits.MAX_TOTAL_ITEMS,
                    EvidenceLimits.MAX_SERIALIZED_BYTES,
                    EvidenceLimits.MAX_AUDIT_EVENTS,
                    EvidenceLimits.MAX_TWIN_ENTITIES,
                    EvidenceLimits.MAX_TWIN_RELATIONSHIPS,
                    EvidenceLimits.MAX_IMPACTED_ENTITIES,
                    EvidenceLimits.MAX_IMPACT_PATHS_PACKET,
                    EvidenceLimits.MAX_RISK_ENTRIES,
                    EvidenceLimits.MAX_POLICY_RULES,
                    EvidenceLimits.MAX_DELTA_STRINGS,
                    EvidenceLimits.MAX_DELTA_STRING_LENGTH,
                    EvidenceLimits.MAX_RISK_REASONS,
                    EvidenceLimits.MAX_TEXT_LENGTH,
                    EvidenceLimits.MAX_LIMITATIONS,
                    EvidenceLimits.MAX_PROPOSAL_OPERATIONS);
        }
    }
}
