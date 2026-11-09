package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.policy.PolicyEvaluationResponse;
import com.soubhagya.systivex.policy.PolicyEvaluator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PolicyEvidenceAdapter implements EvidenceSource<PolicyEvaluationResponse> {

    public PolicyEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(PolicyEvaluationResponse policy) {
        EvidenceItem<PolicyEvidence> item = adaptItem(policy);
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<PolicyEvidence> adaptItem(PolicyEvaluationResponse policy) {
        PolicyEvidence content = toContent(policy);
        if (content == null) {
            return null;
        }

        SimulationEvidenceAdapter.BoundedDelta delta = policy.simulation() == null
                ? new SimulationEvidenceAdapter.BoundedDelta(null, false, 0, 0)
                : SimulationEvidenceAdapter.boundedDelta(policy.simulation().delta());
        int rawRules = nonNullCount(policy.rulesFired());
        int omittedRules = Math.max(0, rawRules - content.rulesFired().size());
        int truncatedDetails = 0;
        for (PolicyEvaluator.FiredRule rule : safe(policy.rulesFired())) {
            if (rule != null && EvidenceLimits.exceeds(rule.detail(), EvidenceLimits.MAX_TEXT_LENGTH)) {
                truncatedDetails++;
            }
        }
        List<String> sourceLimitations = policy.simulation() == null
                ? List.of()
                : safe(policy.simulation().limitations());
        EvidenceLimits.BoundedText boundedSummaryLimitations = boundedSorted(
                sourceLimitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        int omittedSummaryLimitations = Math.max(
                0, nonNullCount(sourceLimitations) - boundedSummaryLimitations.values().size());

        List<String> notices = new ArrayList<>();
        if (omittedRules > 0) {
            notices.add("Policy fired rules capped at " + EvidenceLimits.MAX_POLICY_RULES
                    + "; server reported " + rawRules + " rules; " + omittedRules + " omitted.");
        }
        if (truncatedDetails > 0) {
            notices.add("Policy rule details truncated to " + EvidenceLimits.MAX_TEXT_LENGTH
                    + " characters for " + truncatedDetails + " rules.");
        }
        if (delta.truncated()) {
            notices.add("Policy simulation delta lists capped at "
                    + EvidenceLimits.MAX_DELTA_STRINGS + " entries and "
                    + EvidenceLimits.MAX_DELTA_STRING_LENGTH + " characters; "
                    + delta.omitted() + " entries omitted.");
        }
        if (boundedSummaryLimitations.truncated()) {
            notices.add("Policy simulation limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted.");
        }
        if (EvidenceLimits.exceeds(policy.advisory(), EvidenceLimits.MAX_TEXT_LENGTH)) {
            notices.add("Policy advisory was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }
        if (EvidenceLimits.exceeds(policy.note(), EvidenceLimits.MAX_TEXT_LENGTH)) {
            notices.add("Policy note was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }

        List<String> limitations = new ArrayList<>(notices);
        if (content.simulation() != null) {
            limitations.addAll(content.simulation().limitations());
        }
        EvidenceLimits.BoundedText bounded =
                EvidenceLimits.textList(
                        limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        List<String> finalLimitations = new ArrayList<>(bounded.values());
        boolean truncated = omittedRules > 0
                || truncatedDetails > 0
                || delta.truncated()
                || boundedSummaryLimitations.truncated()
                || EvidenceLimits.exceeds(policy.advisory(), EvidenceLimits.MAX_TEXT_LENGTH)
                || EvidenceLimits.exceeds(policy.note(), EvidenceLimits.MAX_TEXT_LENGTH)
                || bounded.truncated();
        if (bounded.truncated()) {
            String summary = "Policy limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted";
            if (finalLimitations.size() >= EvidenceLimits.MAX_LIMITATIONS) {
                finalLimitations.set(EvidenceLimits.MAX_LIMITATIONS - 1, summary);
            } else {
                finalLimitations.add(summary);
            }
        }

        int included = 1 + content.rulesFired().size() + delta.included()
                + (content.simulation() == null ? 0 : content.simulation().limitations().size());
        int omitted = omittedRules + delta.omitted() + omittedSummaryLimitations;

        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.DETERMINISTIC_FINDING,
                "policy.evaluation",
                "policy:" + rootCount(policy) + " roots",
                policy.policyVersion(),
                List.of(),
                content,
                finalLimitations,
                EvidenceQualification.ADVISORY,
                truncated ? EvidenceSourceStatus.TRUNCATED : EvidenceSourceStatus.AVAILABLE,
                included,
                omitted);
    }

    public static PolicyEvidence toContent(PolicyEvaluationResponse policy) {
        if (policy == null) {
            return null;
        }

        List<PolicyEvidence.FiredRuleEvidence> rules = new ArrayList<>();
        for (PolicyEvaluator.FiredRule rule : safe(policy.rulesFired())) {
            if (rule == null) {
                continue;
            }
            rules.add(new PolicyEvidence.FiredRuleEvidence(
                    rule.code(),
                    rule.outcome(),
                    EvidenceLimits.text(rule.detail())));
        }
        rules.sort(Comparator.comparing(
                        (PolicyEvidence.FiredRuleEvidence rule) -> rule.code(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(rule -> rule.outcome() == null ? "" : rule.outcome().name()));
        List<PolicyEvidence.FiredRuleEvidence> cappedRules =
                EvidenceLimits.first(rules, EvidenceLimits.MAX_POLICY_RULES);

        PolicyEvidence.PolicySimulationSummaryEvidence summary = null;
        if (policy.simulation() != null) {
            SimulationEvidenceAdapter.BoundedDelta delta =
                    SimulationEvidenceAdapter.boundedDelta(policy.simulation().delta());
            EvidenceLimits.BoundedText limitations = boundedSorted(
                    safe(policy.simulation().limitations()),
                    EvidenceLimits.MAX_LIMITATIONS,
                    EvidenceLimits.MAX_TEXT_LENGTH);
            summary = new PolicyEvidence.PolicySimulationSummaryEvidence(
                    graph(policy.simulation().graph()),
                    delta.evidence(),
                    limitations.values());
        }

        return new PolicyEvidence(
                policy.decision(),
                policy.policyVersion(),
                EvidenceLimits.text(policy.advisory()),
                new SimulationEvidence.SimulationSettingsEvidence(
                        policy.settings() == null ? 0 : policy.settings().maxDepth(),
                        includeTypes(policy.settings() == null
                                ? null
                                : policy.settings().includeTypes()),
                        rootCount(policy)),
                cappedRules,
                summary,
                EvidenceLimits.text(policy.note()));
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

    private static List<String> includeTypes(
            java.util.Set<com.soubhagya.systivex.twin.model.SystemEntityType> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return types.stream().map(Enum::name).sorted().toList();
    }

    private static EvidenceLimits.BoundedText boundedSorted(
            List<String> values, int maxEntries, int maxLength) {
        List<String> sorted = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                sorted.add(value);
            }
        }
        sorted.sort(String::compareTo);
        return EvidenceLimits.textList(sorted, maxEntries, maxLength);
    }

    private static int rootCount(PolicyEvaluationResponse policy) {
        return policy.settings() == null ? 0 : policy.settings().rootCount();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
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
}
