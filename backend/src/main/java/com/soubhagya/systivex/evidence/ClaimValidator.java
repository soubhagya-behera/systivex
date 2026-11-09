package com.soubhagya.systivex.evidence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClaimValidator {

    private static final Map<EvidenceCategory, List<String>> CATEGORY_FAMILIES = Map.of(
            EvidenceCategory.OBSERVED_FACT,
                    List.of("twin.entity", "twin.relationship", "twin.runtime"),
            EvidenceCategory.DETERMINISTIC_FINDING,
                    List.of("impact.analysis", "risk.assessment", "policy.evaluation"),
            EvidenceCategory.HYPOTHETICAL_RESULT, List.of("change.simulation"),
            EvidenceCategory.GOVERNANCE_RECORD, List.of("proposal.revision"),
            EvidenceCategory.AUDIT_EVIDENCE, List.of("audit.events"));

    private static final List<EvidenceQualification> PRECEDENCE_WORST_FIRST = List.of(
            EvidenceQualification.MISSING,
            EvidenceQualification.TRUNCATED,
            EvidenceQualification.UNCERTAIN,
            EvidenceQualification.STALE,
            EvidenceQualification.HISTORICAL,
            EvidenceQualification.HYPOTHETICAL,
            EvidenceQualification.ADVISORY,
            EvidenceQualification.CURRENT);

    private ClaimValidator() {
    }

    public static ClaimValidationResult validate(EvidencePacket packet, EvidenceClaim claim) {
        if (packet == null) {
            throw new IllegalArgumentException("Evidence packet is required");
        }
        if (claim == null) {
            throw new IllegalArgumentException("Evidence claim is required");
        }
        if (!claim.packetRef().equals(packet.packetRef()) || claim.mode() != packet.mode()) {
            return ClaimValidationResult.invalid(
                    ClaimFailure.PACKET_IDENTITY,
                    "Claim packet reference or mode does not match the packet under analysis",
                    null);
        }
        Map<String, EvidenceItem<?>> byRef = new HashMap<>();
        for (EvidenceItem<?> item : packet.items()) {
            byRef.put(item.ref().value(), item);
        }
        List<EvidenceItem<?>> referenced = new ArrayList<>();
        for (EvidenceReference reference : claim.evidenceReferences()) {
            EvidenceItem<?> item = byRef.get(reference.value());
            if (item == null) {
                return ClaimValidationResult.invalid(
                        ClaimFailure.REFERENCE_EXISTENCE,
                        "Claim references evidence that is not in this packet: "
                                + reference.value(),
                        null);
            }
            referenced.add(item);
        }
        for (EvidenceItem<?> item : referenced) {
            if (item.category() != claim.category()) {
                return ClaimValidationResult.invalid(
                        ClaimFailure.CATEGORY,
                        "Claim category " + claim.category()
                                + " does not match referenced item category " + item.category()
                                + " (" + item.ref().value() + ")",
                        null);
            }
        }
        EvidenceQualification computed = null;
        for (EvidenceItem<?> item : referenced) {
            EvidenceQualification contribution =
                    contributionFor(item.qualification(), item.status());
            if (contribution == null) {
                return ClaimValidationResult.invalid(
                        ClaimFailure.INVALID_PAIR,
                        "Referenced item " + item.ref().value()
                                + " has an invalid qualification/status pair: "
                                + item.qualification() + "/" + item.status(),
                        null);
            }
            computed = leastFavorable(computed, contribution);
        }
        if (computed != null && rank(claim.qualification()) > rank(computed)) {
            return ClaimValidationResult.invalid(
                    ClaimFailure.QUALIFICATION,
                    "Claim qualification " + claim.qualification()
                            + " is more favorable than the computed qualification " + computed,
                    computed);
        }
        for (EvidenceItem<?> item : referenced) {
            for (String limitation : item.limitations()) {
                if (!claim.limitations().contains(limitation)) {
                    return ClaimValidationResult.invalid(
                            ClaimFailure.LIMITATION,
                            "Claim omits a limitation of referenced item " + item.ref().value()
                                    + ": " + limitation,
                            computed);
                }
            }
        }
        List<EvidenceOmission> blocking = applicableOmissions(packet, claim.category());
        if (!blocking.isEmpty() && !isInsufficiencyClaim(claim, blocking)) {
            EvidenceOmission omission = blocking.get(0);
            return ClaimValidationResult.invalid(
                    ClaimFailure.INSUFFICIENCY,
                    "Source " + omission.source() + " is " + omission.status()
                            + " for this category; only an insufficiency claim is accepted: "
                            + omission.reason(),
                    computed);
        }
        if (ClaimWordingScreen.isForbidden(claim.statement())) {
            return ClaimValidationResult.invalid(
                    ClaimFailure.WORDING,
                    "Claim statement contains a forbidden assertion pattern",
                    computed);
        }
        return ClaimValidationResult.valid(computed);
    }

    static EvidenceQualification contributionFor(
            EvidenceQualification qualification, EvidenceSourceStatus status) {
        if (qualification == null || status == null) {
            return null;
        }
        if (status == EvidenceSourceStatus.OMITTED) {
            return null;
        }
        if (qualification == EvidenceQualification.MISSING) {
            return status == EvidenceSourceStatus.UNAVAILABLE
                    ? EvidenceQualification.MISSING
                    : null;
        }
        if (status == EvidenceSourceStatus.TRUNCATED) {
            return EvidenceQualification.TRUNCATED;
        }
        if (status == EvidenceSourceStatus.UNAVAILABLE) {
            if (qualification == EvidenceQualification.TRUNCATED
                    || qualification == EvidenceQualification.HYPOTHETICAL
                    || qualification == EvidenceQualification.ADVISORY) {
                return null;
            }
            return leastFavorable(EvidenceQualification.UNCERTAIN, qualification);
        }
        return qualification == EvidenceQualification.TRUNCATED ? null : qualification;
    }

    static boolean applicable(EvidenceMode mode, String source) {
        return switch (mode) {
            case BASELINE -> !source.equals("change.simulation")
                    && !source.equals("policy.evaluation");
            case SIMULATION -> !source.equals("impact.analysis")
                    && !source.equals("risk.assessment")
                    && !source.equals("policy.evaluation");
            case POLICY_EVALUATION -> !source.equals("impact.analysis")
                    && !source.equals("risk.assessment");
        };
    }

    static Map<EvidenceCategory, List<String>> categoryFamilies() {
        return CATEGORY_FAMILIES;
    }

    private static List<EvidenceOmission> applicableOmissions(
            EvidencePacket packet, EvidenceCategory category) {
        List<EvidenceOmission> blocking = new ArrayList<>();
        for (EvidenceOmission omission : packet.omissions()) {
            if (omission.status() != EvidenceSourceStatus.OMITTED
                    && omission.status() != EvidenceSourceStatus.UNAVAILABLE) {
                continue;
            }
            if (!CATEGORY_FAMILIES.get(category).contains(omission.source())) {
                continue;
            }
            if (!applicable(packet.mode(), omission.source())) {
                continue;
            }
            blocking.add(omission);
        }
        return blocking;
    }

    private static boolean isInsufficiencyClaim(
            EvidenceClaim claim, List<EvidenceOmission> blocking) {
        if (claim.qualification() != EvidenceQualification.MISSING) {
            return false;
        }
        for (EvidenceOmission omission : blocking) {
            for (String limitation : claim.limitations()) {
                if (limitation.contains(omission.source())
                        || limitation.contains(omission.reason())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static EvidenceQualification leastFavorable(
            EvidenceQualification current, EvidenceQualification candidate) {
        if (current == null) {
            return candidate;
        }
        return rank(candidate) < rank(current) ? candidate : current;
    }

    private static int rank(EvidenceQualification qualification) {
        return PRECEDENCE_WORST_FIRST.indexOf(qualification);
    }
}
