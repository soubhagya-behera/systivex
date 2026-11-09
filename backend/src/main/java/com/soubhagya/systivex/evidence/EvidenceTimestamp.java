package com.soubhagya.systivex.evidence;

import java.time.Instant;
import java.util.List;

public record EvidenceTimestamp(String name, Instant value) {
    public EvidenceTimestamp {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Timestamp name must not be blank");
        }
    }
}
