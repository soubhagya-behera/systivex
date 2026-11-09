package com.soubhagya.systivex.evidence;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ProposalEvidence(
        UUID proposalKey,
        int revisionNumber,
        String status,
        JsonNode operations,
        int operationCount,
        boolean operationsTruncated,
        int includedOperationCount,
        int omittedOperationCount,
        String operationsHash,
        JsonNode assessment,
        String assessmentHash,
        String policyDecision,
        String policyVersion,
        String riskTaxonomy,
        String twinFingerprint,
        Instant snapshotObservedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public ProposalEvidence {
        operations = operations == null ? null : operations.deepCopy();
        assessment = assessment == null ? null : assessment.deepCopy();
        if (operationCount < 0 || includedOperationCount < 0 || omittedOperationCount < 0) {
            throw new IllegalArgumentException("Operation counts must not be negative");
        }
    }

    @Override
    public JsonNode operations() {
        return operations == null ? null : operations.deepCopy();
    }

    @Override
    public JsonNode assessment() {
        return assessment == null ? null : assessment.deepCopy();
    }
}
