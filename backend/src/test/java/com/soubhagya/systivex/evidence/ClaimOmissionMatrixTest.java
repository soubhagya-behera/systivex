package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static com.soubhagya.systivex.evidence.ClaimValidatorTestSupport.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimOmissionMatrixTest {

    @Test
    void categoryFamiliesMatchTheNineS1SourceAdapters() {
        var families = ClaimValidator.categoryFamilies();
        assertThat(families.get(EvidenceCategory.OBSERVED_FACT))
                .containsExactly("twin.entity", "twin.relationship", "twin.runtime");
        assertThat(families.get(EvidenceCategory.DETERMINISTIC_FINDING))
                .containsExactly("impact.analysis", "risk.assessment", "policy.evaluation");
        assertThat(families.get(EvidenceCategory.HYPOTHETICAL_RESULT))
                .containsExactly("change.simulation");
        assertThat(families.get(EvidenceCategory.GOVERNANCE_RECORD))
                .containsExactly("proposal.revision");
        assertThat(families.get(EvidenceCategory.AUDIT_EVIDENCE))
                .containsExactly("audit.events");
        assertThat(families.values().stream().mapToInt(List::size).sum()).isEqualTo(9);
    }

    @Test
    void baselineApplicabilityMatchesAssemblerControlFlow() {
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "twin.entity")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "twin.relationship")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "twin.runtime")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "impact.analysis")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "risk.assessment")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "change.simulation")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "policy.evaluation")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "proposal.revision")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.BASELINE, "audit.events")).isTrue();
    }

    @Test
    void simulationApplicabilityMatchesAssemblerControlFlow() {
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "twin.entity")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "twin.relationship")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "twin.runtime")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "impact.analysis")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "risk.assessment")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "change.simulation")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "policy.evaluation")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "proposal.revision")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.SIMULATION, "audit.events")).isTrue();
    }

    @Test
    void policyApplicabilityMatchesAssemblerControlFlow() {
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "twin.entity")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "twin.relationship")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "twin.runtime")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "impact.analysis")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "risk.assessment")).isFalse();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "change.simulation")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "policy.evaluation")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "proposal.revision")).isTrue();
        assertThat(ClaimValidator.applicable(EvidenceMode.POLICY_EVALUATION, "audit.events")).isTrue();
    }

    @Test
    void applicableOmittedOmissionForcesInsufficiency() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "twin.runtime", "Twin runtime evidence was not requested.",
                        EvidenceSourceStatus.OMITTED)));
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.INSUFFICIENCY);
    }

    @Test
    void notApplicableOmittedOmissionDoesNotBlockSimulationClaimInBaselineShape() {
        EvidencePacket packet = packet(
                EvidenceMode.SIMULATION,
                List.of(item(1, EvidenceCategory.HYPOTHETICAL_RESULT, "change.simulation",
                        EvidenceQualification.HYPOTHETICAL, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "policy.evaluation", "Policy evidence is not included in SIMULATION mode.",
                        EvidenceSourceStatus.OMITTED)));
        EvidenceClaim claim = claim(
                EvidenceMode.SIMULATION,
                EvidenceCategory.HYPOTHETICAL_RESULT,
                EvidenceQualification.HYPOTHETICAL,
                List.of(EvidenceReference.of(1)),
                List.of());

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void notApplicableUnavailableOmissionDoesNotBlock() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.DETERMINISTIC_FINDING, "impact.analysis",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "change.simulation", "Simulation source unavailable.",
                        EvidenceSourceStatus.UNAVAILABLE)));
        EvidenceClaim claim = claim(
                EvidenceCategory.DETERMINISTIC_FINDING, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).valid()).isTrue();
    }

    @Test
    void graphFailurePacketForcesInsufficiencyForGraphCategories() {
        EvidencePacket packet = packet(
                EvidenceMode.BASELINE,
                List.of(item(1, EvidenceCategory.OBSERVED_FACT, "twin.entity",
                        EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE)),
                List.of(
                        new EvidenceOmission(
                                "twin.entity", "graph snapshot truncated and analysis refused",
                                EvidenceSourceStatus.UNAVAILABLE),
                        new EvidenceOmission(
                                "twin.runtime", "graph snapshot truncated and analysis refused",
                                EvidenceSourceStatus.UNAVAILABLE)));
        EvidenceClaim claim = claim(
                EvidenceCategory.OBSERVED_FACT, EvidenceQualification.CURRENT,
                List.of(EvidenceReference.of(1)), List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.INSUFFICIENCY);
    }

    @Test
    void policyModeDeterministicClaimBlockedByApplicablePolicyOmission() {
        EvidencePacket packet = packet(
                EvidenceMode.POLICY_EVALUATION,
                List.of(item(1, EvidenceCategory.DETERMINISTIC_FINDING, "policy.evaluation",
                        EvidenceQualification.ADVISORY, EvidenceSourceStatus.AVAILABLE)),
                List.of(new EvidenceOmission(
                        "policy.evaluation", "Policy evidence is unavailable.",
                        EvidenceSourceStatus.UNAVAILABLE)));
        EvidenceClaim claim = claim(
                EvidenceMode.POLICY_EVALUATION,
                EvidenceCategory.DETERMINISTIC_FINDING,
                EvidenceQualification.ADVISORY,
                List.of(EvidenceReference.of(1)),
                List.of());

        assertThat(ClaimValidator.validate(packet, claim).failure())
                .isEqualTo(ClaimFailure.INSUFFICIENCY);
    }
}
