package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.soubhagya.systivex.evidence.ClaimValidatorTestSupport.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimValidatorTest {

    private static EvidencePacket baselinePacket() {
        return packet(
                EvidenceMode.BASELINE,
                List.of(
                        item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                                EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE),
                        item(2, EvidenceCategory.DETERMINISTIC_FINDING, "impact.analysis",
                                EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(
                        new EvidenceOmission("change.simulation",
                                "Simulation evidence is not applicable to BASELINE mode.",
                                EvidenceSourceStatus.OMITTED),
                        new EvidenceOmission("policy.evaluation",
                                "Policy evidence is not applicable to BASELINE mode.",
                                EvidenceSourceStatus.OMITTED),
                        new EvidenceOmission("proposal.revision",
                                "Proposal evidence was not requested.",
                                EvidenceSourceStatus.OMITTED),
                        new EvidenceOmission("audit.events",
                                "Audit evidence was not requested.",
                                EvidenceSourceStatus.OMITTED)));
    }

    @Test
    void validClaimIsAcceptedWithComputedQualification() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT,
                EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)),
                List.of());

        ClaimValidationResult result = ClaimValidator.validate(packet, claim);

        assertThat(result.valid()).isTrue();
        assertThat(result.failure()).isEqualTo(ClaimFailure.NONE);
        assertThat(result.computedQualification()).isEqualTo(EvidenceQualification.CURRENT);
    }

    @Test
    void nullArgumentsAreRejectedAsProgrammerErrors() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());
        assertThatThrownBy(() -> ClaimValidator.validate(null, claim))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClaimValidator.validate(packet, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fabricatedReferenceIsRejected() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(99)), List.of());

        ClaimValidationResult result = ClaimValidator.validate(packet, claim);

        assertThat(result.valid()).isFalse();
        assertThat(result.failure()).isEqualTo(ClaimFailure.REFERENCE_EXISTENCE);
    }

    @Test
    void packetIdentityMismatchIsRejected() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim wrongPacket = new EvidenceClaim(
                "The evidence describes the recorded state.",
                EvidenceCategory.OBSERVED_FACT,
                List.of(EvidenceReference.of(1)),
                EvidenceQualification.CURRENT,
                List.of(),
                EvidenceReference.packet(2),
                EvidenceMode.BASELINE);
        assertThat(ClaimValidator.validate(packet, wrongPacket).failure())
                .isEqualTo(ClaimFailure.PACKET_IDENTITY);

        EvidenceClaim wrongMode = claim(
                EvidenceMode.SIMULATION,
                EvidenceCategory.OBSERVED_FACT,
                EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)),
                List.of());
        assertThat(ClaimValidator.validate(packet, wrongMode).failure())
                .isEqualTo(ClaimFailure.PACKET_IDENTITY);
    }

    @Test
    void emptyPacketRejectsEveryReference() {
        EvidencePacket packet = packet(EvidenceMode.BASELINE, List.of(), List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.REFERENCE_EXISTENCE);
    }

    @Test
    void categoryMismatchIsRejected() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.GOVERNANCE_RECORD,
                EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)),
                List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.CATEGORY);
    }

    @Test
    void mixedCategoryReferencesAreRejected() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT,
                EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1), EvidenceReference.of(2)),
                List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.CATEGORY);
    }

    @Test
    void invalidPairIsRejected() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.TRUNCATED, EvidenceSourceStatus.AVAILABLE)),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.TRUNCATED,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.INVALID_PAIR);
    }

    @Test
    void moreFavorableQualificationThanComputedIsRejected() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.runtime",
                        EvidenceQualification.STALE, EvidenceSourceStatus.AVAILABLE)),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        ClaimValidationResult result = ClaimValidator.validate(packet, claim);

        assertThat(result.valid()).isFalse();
        assertThat(result.failure()).isEqualTo(ClaimFailure.QUALIFICATION);
        assertThat(result.computedQualification()).isEqualTo(EvidenceQualification.STALE);
    }

    @Test
    void moreConservativeQualificationThanComputedIsAccepted() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.UNCERTAIN,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void unavailableItemDowngradesClaimToUncertain() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.GOVERNANCE_RECORD, "proposal.revision",
                        EvidenceQualification.HISTORICAL, EvidenceSourceStatus.UNAVAILABLE,
                        List.of("Proposal assessment is unavailable."))),
                List.of());
        EvidenceClaim historical = claim(
                EvidenceCategory.GOVERNANCE_RECORD, EvidenceQualification.HISTORICAL,
                List.of(EvidenceReference.of(1)),
                List.of("Proposal assessment is unavailable."));
        EvidenceClaim uncertain = claim(
                EvidenceCategory.GOVERNANCE_RECORD, EvidenceQualification.UNCERTAIN,
                List.of(EvidenceReference.of(1)),
                List.of("Proposal assessment is unavailable."));

        assertThat(ClaimValidator.validate(packet, historical).failure())
                .isEqualTo(ClaimFailure.QUALIFICATION);
        assertThat(ClaimValidator.validate(packet, uncertain).valid()).isTrue();
    }

    @Test
    void leastFavorableContributionWinsAcrossReferences() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(
                        item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                                EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE),
                        item(2, EvidenceCategory.OBSERVED_FACT, "twin.runtime",
                                EvidenceQualification.STALE, EvidenceSourceStatus.AVAILABLE)),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.STALE,
                List.of(EvidenceReference.of(1), EvidenceReference.of(2)), List.of());

        ClaimValidationResult result = ClaimValidator.validate(packet, claim);

        assertThat(result.valid()).isTrue();
        assertThat(result.computedQualification()).isEqualTo(EvidenceQualification.STALE);
    }

    @Test
    void missingLimitationIsRejected() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE,
                        List.of("source limitation text"))),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.LIMITATION);
    }

    @Test
    void inheritedLimitationIsAccepted() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE,
                        List.of("source limitation text"))),
                List.of());
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of("source limitation text"));

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void baselineImpactClaimIsNotBlockedByModeInapplicableOmissions() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.DETERMINISTIC_FINDING,
                EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(2)),
                List.of());

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void applicableUnavailableOmissionForcesInsufficiency() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.GOVERNANCE_RECORD, "proposal.revision",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "proposal.revision", "Proposal evidence is unavailable.",
                        EvidenceSourceStatus.UNAVAILABLE)));
        EvidenceClaim substantive = claim(
                EvidenceCategory.GOVERNANCE_RECORD, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, substantive).failure())
                .isEqualTo(ClaimFailure.INSUFFICIENCY);
    }

    @Test
    void insufficiencyShapedClaimIsAcceptedDespiteApplicableOmission() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.GOVERNANCE_RECORD, "proposal.revision",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "proposal.revision", "Proposal evidence is unavailable.",
                        EvidenceSourceStatus.UNAVAILABLE)));
        EvidenceClaim insufficiency = claim(
                EvidenceCategory.GOVERNANCE_RECORD, EvidenceQualification.MISSING,
                List.of(EvidenceReference.of(1)),
                List.of("proposal.revision unavailable: Proposal evidence is unavailable."));

        assertThat(ClaimValidator.validate(packet, insufficiency).valid()).isTrue();
    }

    @Test
    void omissionInOtherCategoryDoesNotBlockClaim() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "audit.events", "Audit evidence was not requested.",
                        EvidenceSourceStatus.OMITTED)));
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void forbiddenWordingIsRejected() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = new EvidenceClaim(
                "The change is safe to execute.",
                EvidenceCategory.OBSERVED_FACT,
                List.of(EvidenceReference.of(1)),
                EvidenceQualification.CURRENT,
                List.of(),
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE);

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.WORDING);
    }

    @Test
    void validationIsDeterministic() {
        EvidencePacket packet = baselinePacket();
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        ClaimValidationResult first = ClaimValidator.validate(packet, claim);
        ClaimValidationResult second = ClaimValidator.validate(packet, claim);

        assertThat(second).isEqualTo(first);
    }
}
