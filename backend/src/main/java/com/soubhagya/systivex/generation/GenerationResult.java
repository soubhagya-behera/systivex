package com.soubhagya.systivex.generation;

import com.soubhagya.systivex.evidence.EvidenceMode;
import com.soubhagya.systivex.evidence.EvidenceReference;
import java.util.List;

public record GenerationResult(
        EvidenceReference packetRef,
        EvidenceMode mode,
        GenerationStatus status,
        List<GeneratedClaimResult> claims,
        int claimsParsed,
        int claimsValid,
        int claimsInvalid) {

    public GenerationResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        claims = List.copyOf(claims == null ? List.of() : claims);
        if (claimsParsed < 0 || claimsValid < 0 || claimsInvalid < 0) {
            throw new IllegalArgumentException("diagnostic counts must not be negative");
        }
    }
}
