package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceClaimTest {

    @Test
    void validClaimPreservesFieldsAndDefensiveCopies() {
        List<EvidenceReference> references = new ArrayList<>(List.of(EvidenceReference.of(1)));
        List<String> limitations = new ArrayList<>(List.of("limitation-a"));
        EvidenceClaim claim = new EvidenceClaim(
                "Recorded state.",
                EvidenceCategory.OBSERVED_FACT,
                references,
                EvidenceQualification.CURRENT,
                limitations,
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE);

        references.add(EvidenceReference.of(2));
        limitations.add("limitation-b");

        assertThat(claim.statement()).isEqualTo("Recorded state.");
        assertThat(claim.category()).isEqualTo(EvidenceCategory.OBSERVED_FACT);
        assertThat(claim.evidenceReferences()).containsExactly(EvidenceReference.of(1));
        assertThat(claim.limitations()).containsExactly("limitation-a");
        assertThat(claim.packetRef()).isEqualTo(EvidenceReference.packet(1));
        assertThat(claim.mode()).isEqualTo(EvidenceMode.BASELINE);
        assertThatThrownBy(() -> claim.evidenceReferences().add(EvidenceReference.of(3)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> claim.limitations().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> new EvidenceClaim(
                        null, EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", null, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, null,
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        null, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        null, EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankStatementIsRejected() {
        assertThatThrownBy(() -> new EvidenceClaim(
                        "  ", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void excessiveStatementLengthIsRejected() {
        String statement = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        assertThatThrownBy(() -> new EvidenceClaim(
                        statement, EvidenceCategory.OBSERVED_FACT,
                        List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum length");
    }

    @Test
    void emptyReferencesAreRejected() {
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void excessiveReferencesAreRejected() {
        List<EvidenceReference> references = new ArrayList<>();
        for (int i = 1; i <= EvidenceClaim.MAX_REFERENCES_PER_CLAIM + 1; i++) {
            references.add(EvidenceReference.of(i));
        }
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, references,
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void duplicateReferencesAreRejected() {
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT,
                        List.of(EvidenceReference.of(1), EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void malformedEvidenceReferenceIsRejectedByReferenceContract() {
        assertThatThrownBy(() -> EvidenceReference.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("X1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("E0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excessiveAndBlankLimitationsAreRejected() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= EvidenceLimits.MAX_LIMITATIONS; i++) {
            many.add("limitation-" + i);
        }
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, many,
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed the maximum");
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(" "),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        String longLimitation = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(longLimitation),
                        EvidenceReference.packet(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum length");
    }

    @Test
    void nonPacketReferenceIsRejected() {
        assertThatThrownBy(() -> new EvidenceClaim(
                        "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                        EvidenceQualification.CURRENT, List.of(),
                        EvidenceReference.of(1), EvidenceMode.BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P<n> form");
    }

    @Test
    void nullLimitationsNormalizeToEmpty() {
        EvidenceClaim claim = new EvidenceClaim(
                "s", EvidenceCategory.OBSERVED_FACT, List.of(EvidenceReference.of(1)),
                EvidenceQualification.CURRENT, null,
                EvidenceReference.packet(1), EvidenceMode.BASELINE);
        assertThat(claim.limitations()).isEmpty();
    }
}
