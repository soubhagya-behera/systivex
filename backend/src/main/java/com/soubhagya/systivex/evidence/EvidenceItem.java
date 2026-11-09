package com.soubhagya.systivex.evidence;

import java.util.ArrayList;
import java.util.List;

public record EvidenceItem<C>(
        EvidenceReference ref,
        EvidenceCategory category,
        String source,
        String scope,
        String sourceVersion,
        List<EvidenceTimestamp> timestamps,
        C content,
        List<String> limitations,
        EvidenceQualification qualification,
        EvidenceSourceStatus status,
        int includedCount,
        int omittedCount) {

    public EvidenceItem {
        if (ref == null) {
            throw new IllegalArgumentException("Evidence reference must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("Evidence category must not be null");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Evidence source must not be blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("Evidence scope must not be blank");
        }
        if (qualification == null) {
            throw new IllegalArgumentException("Evidence qualification must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Evidence source status must not be null");
        }
        timestamps = List.copyOf(timestamps == null ? List.of() : timestamps);
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
        if (includedCount < 0 || omittedCount < 0) {
            throw new IllegalArgumentException("Evidence counts must not be negative");
        }
    }

    public EvidenceItem<C> withRef(EvidenceReference newRef) {
        return new EvidenceItem<>(
                newRef,
                category,
                source,
                scope,
                sourceVersion,
                timestamps,
                content,
                limitations,
                qualification,
                status,
                includedCount,
                omittedCount);
    }

    public EvidenceItem<C> withTruncation(
            EvidenceSourceStatus newStatus,
            EvidenceQualification newQualification,
            List<String> extraLimitations,
            int newIncludedCount,
            int newOmittedCount) {
        List<String> merged = new ArrayList<>(limitations);
        if (extraLimitations != null) {
            merged.addAll(extraLimitations);
        }
        return new EvidenceItem<>(
                ref,
                category,
                source,
                scope,
                sourceVersion,
                timestamps,
                content,
                merged,
                newQualification,
                newStatus,
                newIncludedCount,
                newOmittedCount);
    }
}
