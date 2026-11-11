package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.evidence.EvidenceCategory;
import com.soubhagya.systivex.evidence.EvidenceItem;
import com.soubhagya.systivex.evidence.EvidenceMode;
import com.soubhagya.systivex.evidence.EvidencePacket;
import com.soubhagya.systivex.evidence.EvidencePacket.EvidencePacketLimits;
import com.soubhagya.systivex.evidence.EvidenceQualification;
import com.soubhagya.systivex.evidence.EvidenceReference;
import com.soubhagya.systivex.evidence.EvidenceSourceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class EvidenceGenerationEvaluationTest {

    @Test
    void diagnosticMetricsAreCalculatedWithoutPersistenceOrAuthority() {
        com.soubhagya.systivex.localmodel.LocalModelAdapter adapter =
                Mockito.mock(com.soubhagya.systivex.localmodel.LocalModelAdapter.class);
        Mockito.when(adapter.infer(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                "{\"claims\":[{\"statement\":\"The evidence describes the recorded state.\","
                        + "\"category\":\"OBSERVED_FACT\",\"evidenceReferences\":[\"E1\"],"
                        + "\"qualification\":\"CURRENT\",\"limitations\":[\"limitation-1\"]}]}");

        EvidencePacket packet = new EvidencePacket(
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE,
                List.of(new EvidenceItem<>(
                        EvidenceReference.of(1),
                        EvidenceCategory.OBSERVED_FACT,
                        "twin.entity",
                        "scope-1",
                        null,
                        List.of(),
                        "content-1",
                        List.of("limitation-1"),
                        EvidenceQualification.CURRENT,
                        EvidenceSourceStatus.AVAILABLE,
                        1,
                        0)),
                EvidencePacketLimits.defaults(),
                List.of());

        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());
        GenerationResult result = service.generate(packet);

        int totalClaims = result.claims().size();
        int validClaims = result.claimsValid();
        int invalidClaims = result.claimsInvalid();
        int existingReferences = result.claims().stream()
                .mapToInt(candidate -> (int) candidate.claim().evidenceReferences().stream()
                        .filter(reference -> "E1".equals(reference.value()))
                        .count())
                .sum();
        int preservedLimitations = result.claims().stream()
                .mapToInt(candidate -> (int) candidate.claim().limitations().stream()
                        .filter("limitation-1"::equals)
                        .count())
                .sum();

        double parseSuccess = result.status() == GenerationStatus.SUCCEEDED ? 1.0 : 0.0;
        double structuralValidity = totalClaims == 0 ? 0.0 : (double) validClaims / totalClaims;
        double referencePrecision = totalClaims == 0 ? 0.0
                : (double) existingReferences / (totalClaims * 1.0);
        double limitationPreservation = totalClaims == 0 ? 0.0
                : (double) preservedLimitations / totalClaims;
        double unsupportedClaimRate = totalClaims == 0 ? 0.0
                : (double) invalidClaims / totalClaims;
        // No human-labelled ground truth exists in these transient fixtures, so this
        // value is a diagnostic proxy for deterministic structural validity only.
        // It is not human-review agreement and does not establish semantic correctness.
        double diagnosticStructuralProxy = structuralValidity;

        assertThat(parseSuccess).isEqualTo(1.0);
        assertThat(structuralValidity).isEqualTo(1.0);
        assertThat(referencePrecision).isEqualTo(1.0);
        assertThat(limitationPreservation).isEqualTo(1.0);
        assertThat(unsupportedClaimRate).isEqualTo(0.0);
        assertThat(diagnosticStructuralProxy).isEqualTo(1.0);

        // The runtime result deliberately exposes no raw prompt or model output.
        assertThat(result.claimsParsed()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCEEDED);
    }
}
