package com.soubhagya.systivex.evidence;

public record EvidenceOmission(String source, String reason, EvidenceSourceStatus status) {
    public EvidenceOmission {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Omission source must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Omission reason must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Omission status must not be null");
        }
    }
}
