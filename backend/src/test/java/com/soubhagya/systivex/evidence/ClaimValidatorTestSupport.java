package com.soubhagya.systivex.evidence;

import java.util.List;

final class ClaimValidatorTestSupport {

    private ClaimValidatorTestSupport() {
    }

    static EvidencePacket packet(
            EvidenceMode mode, List<EvidenceItem<?>> items, List<EvidenceOmission> omissions) {
        return new EvidencePacket(
                EvidenceReference.packet(1),
                mode,
                items,
                EvidencePacket.EvidencePacketLimits.defaults(),
                omissions);
    }

    static EvidenceItem<String> item(
            int index,
            EvidenceCategory category,
            String source,
            EvidenceQualification qualification,
            EvidenceSourceStatus status) {
        return item(index, category, source, qualification, status, List.of());
    }

    static EvidenceItem<String> item(
            int index,
            EvidenceCategory category,
            String source,
            EvidenceQualification qualification,
            EvidenceSourceStatus status,
            List<String> limitations) {
        return new EvidenceItem<>(
                EvidenceReference.of(index),
                category,
                source,
                "scope-" + index,
                null,
                List.of(),
                "content-" + index,
                limitations,
                qualification,
                status,
                1,
                0);
    }

    static EvidenceClaim claim(
            EvidenceCategory category,
            EvidenceQualification qualification,
            List<EvidenceReference> references,
            List<String> limitations) {
        return new EvidenceClaim(
                "The evidence describes the recorded state.",
                category,
                references,
                qualification,
                limitations,
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE);
    }

    static EvidenceClaim claim(
            EvidenceMode mode,
            EvidenceCategory category,
            EvidenceQualification qualification,
            List<EvidenceReference> references,
            List<String> limitations) {
        return new EvidenceClaim(
                "The evidence describes the recorded state.",
                category,
                references,
                qualification,
                limitations,
                EvidenceReference.packet(1),
                mode);
    }
}
