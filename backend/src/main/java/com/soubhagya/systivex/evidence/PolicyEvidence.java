package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.policy.PolicyDecision;
import java.util.List;

public record PolicyEvidence(
        PolicyDecision decision,
        String policyVersion,
        String advisory,
        SimulationEvidence.SimulationSettingsEvidence settings,
        List<FiredRuleEvidence> rulesFired,
        PolicySimulationSummaryEvidence simulation,
        String note) {

    public PolicyEvidence {
        rulesFired = List.copyOf(rulesFired == null ? List.of() : rulesFired);
    }

    public record FiredRuleEvidence(String code, PolicyDecision outcome, String detail) {
    }

    public record PolicySimulationSummaryEvidence(
            SimulationEvidence.SimulationGraphEvidence graph,
            SimulationEvidence.SimulationDeltaEvidence delta,
            List<String> limitations) {
        public PolicySimulationSummaryEvidence {
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
        }
    }
}
