package com.soubhagya.systivex.evidence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record EvidenceClaim(
        String statement,
        EvidenceCategory category,
        List<EvidenceReference> evidenceReferences,
        EvidenceQualification qualification,
        List<String> limitations,
        EvidenceReference packetRef,
        EvidenceMode mode) {

    public static final int MAX_REFERENCES_PER_CLAIM = 20;

    public EvidenceClaim {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("Claim statement must not be blank");
        }
        if (statement.length() > EvidenceLimits.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Claim statement exceeds the maximum length of "
                            + EvidenceLimits.MAX_TEXT_LENGTH + " characters");
        }
        if (category == null) {
            throw new IllegalArgumentException("Claim category must not be null");
        }
        if (evidenceReferences == null || evidenceReferences.isEmpty()) {
            throw new IllegalArgumentException(
                    "Claim must reference at least one evidence item");
        }
        if (evidenceReferences.size() > MAX_REFERENCES_PER_CLAIM) {
            throw new IllegalArgumentException(
                    "Claim must reference at most "
                            + MAX_REFERENCES_PER_CLAIM + " evidence items");
        }
        List<EvidenceReference> references = new ArrayList<>();
        for (EvidenceReference reference : evidenceReferences) {
            if (reference == null) {
                throw new IllegalArgumentException("Claim evidence references must not be null");
            }
            references.add(reference);
        }
        if (new HashSet<>(references).size() != references.size()) {
            throw new IllegalArgumentException("Claim evidence references must not contain duplicates");
        }
        evidenceReferences = List.copyOf(references);
        if (qualification == null) {
            throw new IllegalArgumentException("Claim qualification must not be null");
        }
        if (limitations == null) {
            limitations = List.of();
        }
        if (limitations.size() > EvidenceLimits.MAX_LIMITATIONS) {
            throw new IllegalArgumentException(
                    "Claim limitations exceed the maximum of "
                            + EvidenceLimits.MAX_LIMITATIONS + " entries");
        }
        List<String> checkedLimitations = new ArrayList<>();
        for (String limitation : limitations) {
            if (limitation == null || limitation.isBlank()) {
                throw new IllegalArgumentException("Claim limitations must not be blank");
            }
            if (limitation.length() > EvidenceLimits.MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "Claim limitation exceeds the maximum length of "
                                + EvidenceLimits.MAX_TEXT_LENGTH + " characters");
            }
            checkedLimitations.add(limitation);
        }
        limitations = List.copyOf(checkedLimitations);
        if (packetRef == null) {
            throw new IllegalArgumentException("Claim packet reference must not be null");
        }
        if (!packetRef.value().startsWith("P")) {
            throw new IllegalArgumentException(
                    "Claim packet reference must use the P<n> form: " + packetRef.value());
        }
        if (mode == null) {
            throw new IllegalArgumentException("Claim mode must not be null");
        }
    }
}
