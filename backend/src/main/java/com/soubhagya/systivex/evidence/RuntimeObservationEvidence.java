package com.soubhagya.systivex.evidence;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record RuntimeObservationEvidence(
        String externalRef,
        boolean healthAvailable,
        String healthStatus,
        Instant observedAt,
        Map<String, Long> counters,
        List<String> limitations) {
    public RuntimeObservationEvidence {
        counters = Collections.unmodifiableMap(
                new TreeMap<>(counters == null ? Map.of() : counters));
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }
}
