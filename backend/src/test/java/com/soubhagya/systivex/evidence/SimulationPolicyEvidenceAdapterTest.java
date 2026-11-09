package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.intelligence.ImpactResponse;
import com.soubhagya.systivex.intelligence.RiskResponse;
import com.soubhagya.systivex.intelligence.SimulationDelta;
import com.soubhagya.systivex.intelligence.SimulationGraphSummary;
import com.soubhagya.systivex.intelligence.SimulationResponse;
import com.soubhagya.systivex.intelligence.SimulationSettings;
import com.soubhagya.systivex.policy.PolicyDecision;
import com.soubhagya.systivex.policy.PolicyEvaluationResponse;
import com.soubhagya.systivex.policy.PolicyEvaluator;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationPolicyEvidenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void simulationAdapterMarksHypotheticalAndKeepsAllFourSnapshotTimes() {
        Instant beforeImpact = Instant.parse("2025-12-31T20:00:00Z");
        Instant beforeRisk = Instant.parse("2025-12-31T21:00:00Z");
        Instant afterImpact = Instant.parse("2025-12-31T22:00:00Z");
        Instant afterRisk = Instant.parse("2025-12-31T23:00:00Z");
        SimulationResponse simulation = simulation(
                scope(beforeImpact, beforeRisk, false),
                scope(afterImpact, afterRisk, false),
                delta(List.of(), List.of(), List.of()),
                List.of("simulated only"));

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.category()).isEqualTo(EvidenceCategory.HYPOTHETICAL_RESULT);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.HYPOTHETICAL);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.timestamps()).extracting(EvidenceTimestamp::name)
                .containsExactly(
                        "beforeImpactObservedAt",
                        "beforeRiskObservedAt",
                        "afterImpactObservedAt",
                        "afterRiskObservedAt");
        assertThat(item.timestamps()).extracting(EvidenceTimestamp::value)
                .containsExactly(beforeImpact, beforeRisk, afterImpact, afterRisk);
    }

    @Test
    void simulationAdapterCapsDeltaListsAndLongStrings() {
        List<String> added = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            added.add(String.format("e%03d", i));
        }
        added.add("a".repeat(EvidenceLimits.MAX_DELTA_STRING_LENGTH + 1));
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, false),
                scope(NOW, NOW, false),
                delta(added, List.of(), List.of()),
                List.of());

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        List<String> bounded = item.content().delta().impactedAdded();
        assertThat(bounded).hasSize(EvidenceLimits.MAX_DELTA_STRINGS);
        assertThat(bounded).anyMatch(value -> value.endsWith("[truncated]"));
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.limitations()).anyMatch(value -> value.contains("delta lists capped"));
    }

    @Test
    void simulationAdapterCapsLimitationsAndFlagsThem() {
        List<String> limitations = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            limitations.add("limitation-" + i);
        }
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, false),
                scope(NOW, NOW, false),
                delta(List.of(), List.of(), List.of()),
                limitations);

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.content().limitations()).hasSize(EvidenceLimits.MAX_LIMITATIONS);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Simulation limitations capped"));
    }

    @Test
    void simulationAdapterPropagatesNestedTruncation() {
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, true),
                scope(NOW, NOW, false),
                delta(List.of(), List.of(), List.of()),
                List.of());

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Before impact evidence truncated"));
    }

    @Test
    void simulationAdapterFlagsTruncatedAdvisory() {
        String longAdvisory = "a".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, false),
                scope(NOW, NOW, false),
                delta(List.of(), List.of(), List.of()),
                List.of(),
                longAdvisory);

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.content().advisory()).endsWith("[truncated]");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Simulation advisory was truncated"));
    }

    @Test
    void simulationAdapterPropagatesLocalNestedImpactCap() {
        List<ImpactResponse.ImpactedEntity> impacted = new ArrayList<>();
        for (int i = 0; i < EvidenceLimits.MAX_IMPACTED_ENTITIES + 1; i++) {
            impacted.add(new ImpactResponse.ImpactedEntity(
                    java.util.UUID.randomUUID(),
                    SystemEntityType.SERVICE,
                    "entity-" + i,
                    String.format("e%03d", i),
                    1,
                    "start"));
        }
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, false, impacted),
                scope(NOW, NOW, false),
                delta(List.of(), List.of(), List.of()),
                List.of());

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.content().before().impact().impacted())
                .hasSize(EvidenceLimits.MAX_IMPACTED_ENTITIES);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Before impact evidence truncated"));
    }

    @Test
    void simulationAdapterSortsIncludeTypes() {
        SimulationResponse simulation = simulation(
                scope(NOW, NOW, false),
                scope(NOW, NOW, false),
                delta(List.of(), List.of(), List.of()),
                List.of());

        EvidenceItem<SimulationEvidence> item = SimulationEvidenceAdapter.adaptItem(simulation);

        assertThat(item.content().settings().includeTypes())
                .containsExactly("API", "DATABASE", "SERVICE");
    }

    @Test
    void policyAdapterIsAdvisoryAndSortsRules() {
        PolicyEvaluationResponse policy = policy(
                PolicyDecision.REVIEW,
                List.of(
                        new PolicyEvaluator.FiredRule("b.rule", PolicyDecision.REVIEW, "b detail"),
                        new PolicyEvaluator.FiredRule("a.rule", PolicyDecision.ALLOW, "a detail")),
                summary(List.of(), List.of()),
                "advisory only",
                "nothing executed");

        EvidenceItem<PolicyEvidence> item = PolicyEvidenceAdapter.adaptItem(policy);

        assertThat(item.category()).isEqualTo(EvidenceCategory.DETERMINISTIC_FINDING);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.ADVISORY);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.content().decision()).isEqualTo(PolicyDecision.REVIEW);
        assertThat(item.content().rulesFired()).extracting(PolicyEvidence.FiredRuleEvidence::code)
                .containsExactly("a.rule", "b.rule");
    }

    @Test
    void policyEvidenceStructurallyExcludesSimulationScopes() {
        assertThat(PolicyEvidence.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("before", "after", "impact", "risk", "scopes");
    }

    @Test
    void policyAdapterCapsRulesAndDetails() {
        List<PolicyEvaluator.FiredRule> rules = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            rules.add(new PolicyEvaluator.FiredRule(
                    String.format("r%03d", i), PolicyDecision.ALLOW, "detail"));
        }
        rules.add(new PolicyEvaluator.FiredRule(
                "long.detail", PolicyDecision.ALLOW, "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1)));
        PolicyEvaluationResponse policy = policy(
                PolicyDecision.ALLOW, rules, summary(List.of(), List.of()), "advisory", "note");

        EvidenceItem<PolicyEvidence> item = PolicyEvidenceAdapter.adaptItem(policy);

        assertThat(item.content().rulesFired()).hasSize(EvidenceLimits.MAX_POLICY_RULES);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(2);
        assertThat(item.limitations()).anyMatch(value -> value.contains("fired rules capped"));
    }

    @Test
    void policyAdapterCapsSimulationDeltaAndLimitations() {
        List<String> added = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            added.add(String.format("e%03d", i));
        }
        List<String> limitations = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            limitations.add("limitation-" + i);
        }
        PolicyEvaluationResponse policy = policy(
                PolicyDecision.REVIEW,
                List.of(),
                summary(added, limitations),
                "advisory",
                "note");

        EvidenceItem<PolicyEvidence> item = PolicyEvidenceAdapter.adaptItem(policy);

        assertThat(item.content().simulation().delta().impactedAdded())
                .hasSize(EvidenceLimits.MAX_DELTA_STRINGS);
        assertThat(item.content().simulation().limitations())
                .hasSize(EvidenceLimits.MAX_LIMITATIONS);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Policy simulation delta lists capped"));
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Policy simulation limitations capped"));
    }

    @Test
    void policyAdapterTruncatesAdvisoryAndNote() {
        String longText = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        PolicyEvaluationResponse policy = policy(
                PolicyDecision.ALLOW,
                List.of(),
                summary(List.of(), List.of()),
                longText,
                longText);

        EvidenceItem<PolicyEvidence> item = PolicyEvidenceAdapter.adaptItem(policy);

        assertThat(item.content().advisory()).endsWith("[truncated]");
        assertThat(item.content().note()).endsWith("[truncated]");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
    }

    private static SimulationResponse simulation(
            SimulationResponse.Scope before,
            SimulationResponse.Scope after,
            SimulationDelta delta,
            List<String> limitations) {
        return simulation(before, after, delta, limitations, "SIMULATED - NOT EXECUTED");
    }

    private static SimulationResponse simulation(
            SimulationResponse.Scope before,
            SimulationResponse.Scope after,
            SimulationDelta delta,
            List<String> limitations,
            String advisory) {
        return new SimulationResponse(
                true,
                advisory,
                new SimulationSettings(
                        6,
                        EnumSet.of(
                                SystemEntityType.SERVICE,
                                SystemEntityType.API,
                                SystemEntityType.DATABASE),
                        1),
                before,
                after,
                new SimulationGraphSummary(10, 9, 12, 11, 1, 1, 2),
                delta,
                limitations);
    }

    private static SimulationResponse.Scope scope(
            Instant impactObservedAt, Instant riskObservedAt, boolean impactTruncated) {
        return scope(impactObservedAt, riskObservedAt, impactTruncated, List.of());
    }

    private static SimulationResponse.Scope scope(
            Instant impactObservedAt,
            Instant riskObservedAt,
            boolean impactTruncated,
            List<ImpactResponse.ImpactedEntity> impacted) {
        ImpactResponse impact = new ImpactResponse(
                List.of(),
                impacted,
                List.of(),
                6,
                impacted.size(),
                0,
                impactTruncated,
                impactTruncated ? "cap" : null,
                false,
                impactObservedAt,
                10,
                10,
                new ImpactResponse.Limits(10, 10, 500, 2000),
                "impact note");
        RiskResponse risk = new RiskResponse(
                List.of(),
                List.of(),
                6,
                "risk-v1",
                riskObservedAt,
                10,
                10,
                "risk note");
        return new SimulationResponse.Scope(impact, risk);
    }

    private static SimulationDelta delta(
            List<String> added, List<String> removed,
            List<SimulationDelta.RiskLevelChange> changes) {
        return new SimulationDelta(
                added.size(),
                added.size(),
                added.size(),
                removed.size(),
                added,
                removed,
                0,
                0,
                0,
                0,
                changes,
                0,
                0,
                false);
    }

    private static PolicyEvaluationResponse.SimulationSummary summary(
            List<String> added, List<String> limitations) {
        return new PolicyEvaluationResponse.SimulationSummary(
                new SimulationGraphSummary(10, 9, 12, 11, 1, 1, 2),
                delta(added, List.of(), List.of()),
                limitations);
    }

    private static PolicyEvaluationResponse policy(
            PolicyDecision decision,
            List<PolicyEvaluator.FiredRule> rules,
            PolicyEvaluationResponse.SimulationSummary summary,
            String advisory,
            String note) {
        return new PolicyEvaluationResponse(
                decision,
                "policy-v1",
                advisory,
                new SimulationSettings(
                        6,
                        EnumSet.of(SystemEntityType.SERVICE, SystemEntityType.API),
                        1),
                rules,
                summary,
                note);
    }
}
