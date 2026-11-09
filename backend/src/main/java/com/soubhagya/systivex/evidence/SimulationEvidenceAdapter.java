package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.ImpactResponse;
import com.soubhagya.systivex.intelligence.RiskResponse;
import com.soubhagya.systivex.intelligence.SimulationDelta;
import com.soubhagya.systivex.intelligence.SimulationResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SimulationEvidenceAdapter implements EvidenceSource<SimulationResponse> {

    public SimulationEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(SimulationResponse simulation) {
        EvidenceItem<SimulationEvidence> item = adaptItem(simulation);
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<SimulationEvidence> adaptItem(SimulationResponse simulation) {
        SimulationEvidence content = toContent(simulation);
        if (content == null) {
            return null;
        }

        BoundedDelta delta = boundedDelta(simulation.delta());
        boolean nestedTruncated = isImpactTruncated(simulation.before())
                || isRiskTruncated(simulation.before())
                || isImpactTruncated(simulation.after())
                || isRiskTruncated(simulation.after());
        boolean limitationsTruncated = limitationTruncated(simulation.limitations());
        boolean advisoryTruncated =
                EvidenceLimits.exceeds(simulation.advisory(), EvidenceLimits.MAX_TEXT_LENGTH);

        List<String> notices = new ArrayList<>();
        if (delta.truncated()) {
            notices.add("Simulation delta lists capped at " + EvidenceLimits.MAX_DELTA_STRINGS
                    + " entries and " + EvidenceLimits.MAX_DELTA_STRING_LENGTH
                    + " characters; " + delta.omitted() + " entries omitted.");
        }
        if (isImpactTruncated(simulation.before())) {
            notices.add("Before impact evidence truncated.");
        }
        if (isRiskTruncated(simulation.before())) {
            notices.add("Before risk evidence truncated.");
        }
        if (isImpactTruncated(simulation.after())) {
            notices.add("After impact evidence truncated.");
        }
        if (isRiskTruncated(simulation.after())) {
            notices.add("After risk evidence truncated.");
        }
        if (limitationsTruncated) {
            notices.add("Simulation limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted.");
        }
        if (advisoryTruncated) {
            notices.add("Simulation advisory was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }

        List<String> limitations = new ArrayList<>(notices);
        limitations.addAll(content.limitations());
        EvidenceLimits.BoundedText bounded =
                EvidenceLimits.textList(
                        limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        List<String> finalLimitations = new ArrayList<>(bounded.values());
        boolean truncated = delta.truncated() || nestedTruncated || limitationsTruncated
                || advisoryTruncated || bounded.truncated();
        if (bounded.truncated()) {
            String summary = "Simulation limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted";
            if (finalLimitations.size() >= EvidenceLimits.MAX_LIMITATIONS) {
                finalLimitations.set(EvidenceLimits.MAX_LIMITATIONS - 1, summary);
            } else {
                finalLimitations.add(summary);
            }
        }

        int included = 1
                + impactRows(content.before() == null ? null : content.before().impact())
                + riskRows(content.before() == null ? null : content.before().risk())
                + impactRows(content.after() == null ? null : content.after().impact())
                + riskRows(content.after() == null ? null : content.after().risk())
                + delta.included()
                + content.limitations().size();
        int omitted = delta.omitted()
                + omittedImpact(simulation.before())
                + omittedRisk(simulation.before())
                + omittedImpact(simulation.after())
                + omittedRisk(simulation.after());

        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.HYPOTHETICAL_RESULT,
                "change.simulation",
                "simulation:" + rootCount(simulation) + " roots",
                null,
                timestamps(simulation),
                content,
                finalLimitations,
                EvidenceQualification.HYPOTHETICAL,
                truncated ? EvidenceSourceStatus.TRUNCATED : EvidenceSourceStatus.AVAILABLE,
                included,
                omitted);
    }

    public static SimulationEvidence toContent(SimulationResponse simulation) {
        if (simulation == null) {
            return null;
        }
        BoundedDelta delta = boundedDelta(simulation.delta());
        List<String> limitationValues = new ArrayList<>();
        for (String limitation : safe(simulation.limitations())) {
            if (limitation != null) {
                limitationValues.add(limitation);
            }
        }
        limitationValues.sort(String::compareTo);
        EvidenceLimits.BoundedText boundedLimitations = EvidenceLimits.textList(
                limitationValues, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);

        return new SimulationEvidence(
                simulation.simulated(),
                EvidenceLimits.text(simulation.advisory()),
                new SimulationEvidence.SimulationSettingsEvidence(
                        simulation.settings() == null ? 0 : simulation.settings().maxDepth(),
                        includeTypes(simulation.settings() == null
                                ? null
                                : simulation.settings().includeTypes()),
                        rootCount(simulation)),
                scope(simulation.before()),
                scope(simulation.after()),
                graph(simulation.graph()),
                delta.evidence(),
                boundedLimitations.values());
    }

    private static SimulationEvidence.ScopeEvidence scope(SimulationResponse.Scope scope) {
        if (scope == null) {
            return null;
        }
        return new SimulationEvidence.ScopeEvidence(
                ImpactEvidenceAdapter.toContent(scope.impact()),
                RiskEvidenceAdapter.toContent(scope.risk()));
    }

    private static SimulationEvidence.SimulationGraphEvidence graph(
            com.soubhagya.systivex.intelligence.SimulationGraphSummary graph) {
        if (graph == null) {
            return null;
        }
        return new SimulationEvidence.SimulationGraphEvidence(
                graph.baselineEntityCount(),
                graph.hypotheticalEntityCount(),
                graph.baselineRelationshipCount(),
                graph.hypotheticalRelationshipCount(),
                graph.removedEntityCount(),
                graph.addedRelationshipCount(),
                graph.removedRelationshipCount());
    }

    static BoundedDelta boundedDelta(SimulationDelta delta) {
        if (delta == null) {
            return new BoundedDelta(null, false, 0, 0);
        }

        List<String> added = new ArrayList<>();
        for (String value : safe(delta.impactedAdded())) {
            if (value != null) {
                added.add(value);
            }
        }
        added.sort(String::compareTo);
        EvidenceLimits.BoundedText addedBounded = EvidenceLimits.textList(
                added, EvidenceLimits.MAX_DELTA_STRINGS, EvidenceLimits.MAX_DELTA_STRING_LENGTH);

        List<String> removed = new ArrayList<>();
        for (String value : safe(delta.impactedRemoved())) {
            if (value != null) {
                removed.add(value);
            }
        }
        removed.sort(String::compareTo);
        EvidenceLimits.BoundedText removedBounded = EvidenceLimits.textList(
                removed, EvidenceLimits.MAX_DELTA_STRINGS, EvidenceLimits.MAX_DELTA_STRING_LENGTH);

        List<SimulationEvidence.RiskLevelChangeEvidence> changes = new ArrayList<>();
        boolean changesTextTruncated = false;
        for (SimulationDelta.RiskLevelChange change : safe(delta.riskLevelChanges())) {
            if (change == null) {
                continue;
            }
            if (EvidenceLimits.exceeds(
                    change.entityRef(), EvidenceLimits.MAX_DELTA_STRING_LENGTH)) {
                changesTextTruncated = true;
            }
            changes.add(new SimulationEvidence.RiskLevelChangeEvidence(
                    EvidenceLimits.text(change.entityRef(), EvidenceLimits.MAX_DELTA_STRING_LENGTH),
                    change.before(),
                    change.after()));
        }
        changes.sort(Comparator.comparing(
                        (SimulationEvidence.RiskLevelChangeEvidence change) -> change.entityRef(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(change -> change.before() == null ? "" : change.before().name())
                .thenComparing(change -> change.after() == null ? "" : change.after().name()));

        int rawChanges = nonNullCount(delta.riskLevelChanges());
        boolean changesTruncated = changes.size() > EvidenceLimits.MAX_DELTA_STRINGS;
        List<SimulationEvidence.RiskLevelChangeEvidence> cappedChanges =
                EvidenceLimits.first(changes, EvidenceLimits.MAX_DELTA_STRINGS);

        int omitted = Math.max(0, added.size() - addedBounded.values().size())
                + Math.max(0, removed.size() - removedBounded.values().size())
                + Math.max(0, rawChanges - cappedChanges.size());
        boolean truncated = addedBounded.truncated() || removedBounded.truncated()
                || changesTruncated || changesTextTruncated;

        SimulationEvidence.SimulationDeltaEvidence evidence =
                new SimulationEvidence.SimulationDeltaEvidence(
                        delta.beforeImpactedCount(),
                        delta.afterImpactedCount(),
                        delta.impactedAddedCount(),
                        delta.impactedRemovedCount(),
                        addedBounded.values(),
                        removedBounded.values(),
                        delta.beforeRiskCount(),
                        delta.afterRiskCount(),
                        delta.riskAddedCount(),
                        delta.riskRemovedCount(),
                        cappedChanges,
                        delta.uncertainBeforeCount(),
                        delta.uncertainAfterCount(),
                        delta.truncationChanged());

        int included = addedBounded.values().size()
                + removedBounded.values().size()
                + cappedChanges.size();
        return new BoundedDelta(evidence, truncated, omitted, included);
    }

    private static List<EvidenceTimestamp> timestamps(SimulationResponse simulation) {
        List<EvidenceTimestamp> timestamps = new ArrayList<>();
        addTimestamp(timestamps, "beforeImpactObservedAt",
                simulation.before() == null || simulation.before().impact() == null
                        ? null : simulation.before().impact().snapshotObservedAt());
        addTimestamp(timestamps, "beforeRiskObservedAt",
                simulation.before() == null || simulation.before().risk() == null
                        ? null : simulation.before().risk().snapshotObservedAt());
        addTimestamp(timestamps, "afterImpactObservedAt",
                simulation.after() == null || simulation.after().impact() == null
                        ? null : simulation.after().impact().snapshotObservedAt());
        addTimestamp(timestamps, "afterRiskObservedAt",
                simulation.after() == null || simulation.after().risk() == null
                        ? null : simulation.after().risk().snapshotObservedAt());
        return timestamps;
    }

    private static void addTimestamp(List<EvidenceTimestamp> timestamps, String name,
            java.time.Instant value) {
        if (value != null) {
            timestamps.add(new EvidenceTimestamp(name, value));
        }
    }

    private static List<String> includeTypes(Set<com.soubhagya.systivex.twin.model.SystemEntityType> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream().map(Enum::name).sorted().toList();
    }

    private static boolean isImpactTruncated(SimulationResponse.Scope scope) {
        if (scope == null || scope.impact() == null) {
            return false;
        }
        ImpactEvidence content = ImpactEvidenceAdapter.toContent(scope.impact());
        return content != null && content.truncated();
    }

    private static boolean isRiskTruncated(SimulationResponse.Scope scope) {
        if (scope == null || scope.risk() == null) {
            return false;
        }
        RiskEvidence content = RiskEvidenceAdapter.toContent(scope.risk());
        return content != null && content.truncated();
    }

    private static boolean limitationTruncated(List<String> limitations) {
        return limitations != null
                && (limitations.size() > EvidenceLimits.MAX_LIMITATIONS
                        || limitations.stream().anyMatch(
                                value -> EvidenceLimits.exceeds(
                                        value, EvidenceLimits.MAX_TEXT_LENGTH)));
    }

    private static int rootCount(SimulationResponse simulation) {
        return simulation.settings() == null ? 0 : simulation.settings().rootCount();
    }

    private static int impactRows(ImpactEvidence impact) {
        return impact == null
                ? 0
                : impact.startedFrom().size() + impact.impacted().size() + impact.paths().size();
    }

    private static int riskRows(RiskEvidence risk) {
        return risk == null ? 0 : risk.startedFrom().size() + risk.risks().size();
    }

    private static int omittedImpact(SimulationResponse.Scope scope) {
        if (scope == null || scope.impact() == null) {
            return 0;
        }
        ImpactResponse source = scope.impact();
        ImpactEvidence content = ImpactEvidenceAdapter.toContent(source);
        if (content == null) {
            return 0;
        }
        return Math.max(0, size(source.startedFrom()) - content.startedFrom().size())
                + Math.max(0, size(source.impacted()) - content.impacted().size())
                + Math.max(0, size(source.paths()) - content.paths().size());
    }

    private static int omittedRisk(SimulationResponse.Scope scope) {
        if (scope == null || scope.risk() == null) {
            return 0;
        }
        RiskResponse source = scope.risk();
        RiskEvidence content = RiskEvidenceAdapter.toContent(source);
        if (content == null) {
            return 0;
        }
        int omittedReasons = content.risks().stream()
                .mapToInt(RiskEvidence.RiskEntryEvidence::omittedReasonCount)
                .sum();
        return Math.max(0, size(source.startedFrom()) - content.startedFrom().size())
                + Math.max(0, nonNullCount(source.risks()) - content.risks().size())
                + omittedReasons;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static int nonNullCount(List<?> values) {
        if (values == null) {
            return 0;
        }
        int count = 0;
        for (Object value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    record BoundedDelta(
            SimulationEvidence.SimulationDeltaEvidence evidence,
            boolean truncated,
            int omitted,
            int included) {
    }
}
