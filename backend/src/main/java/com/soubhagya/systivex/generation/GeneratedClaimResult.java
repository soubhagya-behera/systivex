package com.soubhagya.systivex.generation;

import com.soubhagya.systivex.evidence.EvidenceClaim;
import com.soubhagya.systivex.evidence.ClaimValidationResult;
import java.util.List;

public record GeneratedClaimResult(
        EvidenceClaim claim,
        ClaimValidationResult validationResult) {

    public GeneratedClaimResult {
        if (claim == null) {
            throw new IllegalArgumentException("claim must not be null");
        }
        if (validationResult == null) {
            throw new IllegalArgumentException("validationResult must not be null");
        }
    }
}
