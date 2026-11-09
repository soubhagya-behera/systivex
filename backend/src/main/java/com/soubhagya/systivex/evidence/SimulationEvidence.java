package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.RiskLevel;
import java.util.List;
import java.util.UUID;

public record SimulationEvidence(
        boolean simulated,
        String advisory,
        SimulationSettingsEvidence settings,
        ScopeEvidence before,
        ScopeEvidence after,
        SimulationGraphEvidence graph,
        SimulationDeltaEvidence delta,
        List<String> limitations) {

    public SimulationEvidence {
        limitations = List.copyOf(limitations == null ? List.of() : limitations);
    }

    public record ScopeEvidence(ImpactEvidence impact, RiskEvidence risk) {
    }

    public record SimulationSettingsEvidence(int maxDepth, List<String> includeTypes, int rootCount) {
        public SimulationSettingsEvidence {
            includeTypes = List.copyOf(includeTypes == null ? List.of() : includeTypes);
        }
    }

    public record SimulationGraphEvidence(
            int baselineEntityCount,
            int hypotheticalEntityCount,
            int baselineRelationshipCount,
            int hypotheticalRelationshipCount,
            int removedEntityCount,
            int addedRelationshipCount,
            int removedRelationshipCount) {
    }

    public record SimulationDeltaEvidence(
            int beforeImpactedCount,
            int afterImpactedCount,
            int impactedAddedCount,
            int impactedRemovedCount,
            List<String> impactedAdded,
            List<String> impactedRemoved,
            int beforeRiskCount,
            int afterRiskCount,
            int riskAddedCount,
            int riskRemovedCount,
            List<RiskLevelChangeEvidence> riskLevelChanges,
            int uncertainBeforeCount,
            int uncertainAfterCount,
            boolean truncationChanged) {
        public SimulationDeltaEvidence {
            impactedAdded = List.copyOf(impactedAdded == null ? List.of() : impactedAdded);
            impactedRemoved = List.copyOf(impactedRemoved == null ? List.of() : impactedRemoved);
            riskLevelChanges = List.copyOf(riskLevelChanges == null ? List.of() : riskLevelChanges);
        }
    }

    public record RiskLevelChangeEvidence(String entityRef, RiskLevel before, RiskLevel after) {
    }
}
