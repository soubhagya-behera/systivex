package com.soubhagya.systivex.evidence;

import java.util.List;

public record AuditEvidence(
        List<AuditEventEvidence> events,
        boolean truncated,
        int limit,
        int omittedCount) {
    public AuditEvidence {
        events = List.copyOf(events == null ? List.of() : events);
        if (limit < 0 || omittedCount < 0) {
            throw new IllegalArgumentException("Audit counts must not be negative");
        }
    }
}
