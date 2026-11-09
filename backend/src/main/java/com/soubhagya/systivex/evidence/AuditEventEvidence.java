package com.soubhagya.systivex.evidence;

import java.time.Instant;
import java.util.UUID;

public record AuditEventEvidence(
        UUID id,
        long seq,
        Instant occurredAt,
        String action,
        String outcome,
        UUID proposalKey,
        int revisionNumber,
        String operationsHash,
        String assessmentHash,
        String prevHash,
        String eventHash) {
}
