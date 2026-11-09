package com.soubhagya.systivex.evidence;

public record ClaimValidationResult(
        boolean valid,
        ClaimFailure failure,
        String detail,
        EvidenceQualification computedQualification) {

    public ClaimValidationResult {
        if (failure == null) {
            throw new IllegalArgumentException("Claim validation failure must not be null");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("Claim validation detail must not be blank");
        }
    }

    public static ClaimValidationResult valid(EvidenceQualification computedQualification) {
        return new ClaimValidationResult(
                true, ClaimFailure.NONE, "Claim is valid", computedQualification);
    }

    public static ClaimValidationResult invalid(
            ClaimFailure failure, String detail, EvidenceQualification computedQualification) {
        if (failure == ClaimFailure.NONE) {
            throw new IllegalArgumentException("Invalid results must carry a failure class");
        }
        return new ClaimValidationResult(false, failure, detail, computedQualification);
    }
}
