package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.ImpactResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ImpactEvidenceAdapter implements EvidenceSource<ImpactResponse> {

    public ImpactEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(ImpactResponse impact) {
        EvidenceItem<ImpactEvidence> item = adaptItem(impact);
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<ImpactEvidence> adaptItem(ImpactResponse impact) {
        ImpactEvidence content = toContent(impact);
        if (content == null) {
            return null;
        }
        List<String> limitations = new ArrayList<>();
        if (impact.note() != null && !impact.note().isBlank()) {
            limitations.add(EvidenceLimits.text(impact.note()));
        }
        if (EvidenceLimits.exceeds(impact.note(), EvidenceLimits.MAX_TEXT_LENGTH)) {
            limitations.add("Impact note was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }
        if (impact.truncated()) {
            String reason = impact.truncationReason() == null || impact.truncationReason().isBlank()
                    ? ""
                    : " Reason: " + EvidenceLimits.text(impact.truncationReason());
            limitations.add("Server reported truncated impact results." + reason);
        }
        if (EvidenceLimits.exceeds(impact.truncationReason(), EvidenceLimits.MAX_TEXT_LENGTH)) {
            limitations.add("Impact truncation reason was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }
        boolean collectionTruncated = size(impact.startedFrom()) > EvidenceLimits.MAX_ROOTS
                || size(impact.impacted()) > EvidenceLimits.MAX_IMPACTED_ENTITIES
                || size(impact.paths()) > EvidenceLimits.MAX_IMPACT_PATHS_PACKET;
        if (collectionTruncated && !impact.truncated()) {
            limitations.add("Impact evidence capped at "
                    + EvidenceLimits.MAX_ROOTS + " starts, "
                    + EvidenceLimits.MAX_IMPACTED_ENTITIES + " impacted entities, and "
                    + EvidenceLimits.MAX_IMPACT_PATHS_PACKET + " paths; server reported "
                    + size(impact.startedFrom()) + " starts, "
                    + size(impact.impacted()) + " impacted entities, and "
                    + size(impact.paths()) + " paths.");
        }
        EvidenceLimits.BoundedText bounded =
                EvidenceLimits.textList(
                        limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        List<String> finalLimitations = new ArrayList<>(bounded.values());
        boolean truncated = content.truncated() || bounded.truncated();
        if (bounded.truncated()) {
            String summary = "Impact limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted";
            if (finalLimitations.size() >= EvidenceLimits.MAX_LIMITATIONS) {
                finalLimitations.set(EvidenceLimits.MAX_LIMITATIONS - 1, summary);
            } else {
                finalLimitations.add(summary);
            }
        }

        int included = content.startedFrom().size()
                + content.impacted().size()
                + content.paths().size();
        int omitted = Math.max(0, size(impact.startedFrom()) - content.startedFrom().size())
                + Math.max(0, size(impact.impacted()) - content.impacted().size())
                + Math.max(0, size(impact.paths()) - content.paths().size());

        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.DETERMINISTIC_FINDING,
                "impact.analysis",
                "impact:" + size(impact.startedFrom()) + " roots",
                null,
                impact.snapshotObservedAt() == null
                        ? List.of()
                        : List.of(new EvidenceTimestamp(
                                "snapshotObservedAt", impact.snapshotObservedAt())),
                content,
                finalLimitations,
                truncated ? EvidenceQualification.TRUNCATED : EvidenceQualification.CURRENT,
                truncated ? EvidenceSourceStatus.TRUNCATED : EvidenceSourceStatus.AVAILABLE,
                included,
                omitted);
    }

    public static ImpactEvidence toContent(ImpactResponse impact) {
        if (impact == null) {
            return null;
        }

        List<ImpactEvidence.ImpactStartEvidence> starts = new ArrayList<>();
        for (ImpactResponse.StartEntity start : safe(impact.startedFrom())) {
            if (start == null) {
                continue;
            }
            starts.add(new ImpactEvidence.ImpactStartEvidence(
                    start.id(),
                    start.type(),
                    EvidenceLimits.text(start.name()),
                    EvidenceLimits.text(start.externalRef()),
                    EvidenceLimits.text(start.environment())));
        }
        starts.sort(Comparator.comparing(
                        (ImpactEvidence.ImpactStartEvidence start) -> start.externalRef(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(start -> start.id() == null ? "" : start.id().toString()));

        List<ImpactEvidence.ImpactedEntityEvidence> impacted = new ArrayList<>();
        for (ImpactResponse.ImpactedEntity entity : safe(impact.impacted())) {
            if (entity == null) {
                continue;
            }
            impacted.add(new ImpactEvidence.ImpactedEntityEvidence(
                    entity.id(),
                    entity.type(),
                    EvidenceLimits.text(entity.name()),
                    EvidenceLimits.text(entity.externalRef()),
                    entity.depth(),
                    entity.viaStart()));
        }
        impacted.sort(Comparator.comparing(
                        (ImpactEvidence.ImpactedEntityEvidence entity) -> entity.externalRef(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(entity -> entity.id() == null ? "" : entity.id().toString()));

        List<ImpactEvidence.ImpactPathEvidence> paths = new ArrayList<>();
        for (ImpactResponse.ImpactPath path : safe(impact.paths())) {
            if (path == null) {
                continue;
            }
            List<ImpactEvidence.ImpactStepEvidence> steps = new ArrayList<>();
            for (ImpactResponse.ImpactStep step : safe(path.steps())) {
                if (step == null) {
                    continue;
                }
                steps.add(new ImpactEvidence.ImpactStepEvidence(
                        step.sourceId(),
                        EvidenceLimits.text(step.sourceExternalRef()),
                        step.targetId(),
                        EvidenceLimits.text(step.targetExternalRef()),
                        step.type(),
                        step.direction() == null ? null : step.direction().name()));
            }
            paths.add(new ImpactEvidence.ImpactPathEvidence(
                    path.targetId(),
                    EvidenceLimits.text(path.targetExternalRef()),
                    path.depth(),
                    steps,
                    EvidenceLimits.text(path.explanation())));
        }
        paths.sort(Comparator.comparing(
                        (ImpactEvidence.ImpactPathEvidence path) -> path.targetExternalRef(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(path -> path.targetId() == null ? "" : path.targetId().toString())
                .thenComparingInt(ImpactEvidence.ImpactPathEvidence::depth));

        boolean localTruncated = starts.size() > EvidenceLimits.MAX_ROOTS
                || impacted.size() > EvidenceLimits.MAX_IMPACTED_ENTITIES
                || paths.size() > EvidenceLimits.MAX_IMPACT_PATHS_PACKET
                || EvidenceLimits.exceeds(impact.note(), EvidenceLimits.MAX_TEXT_LENGTH)
                || EvidenceLimits.exceeds(
                        impact.truncationReason(), EvidenceLimits.MAX_TEXT_LENGTH);
        List<ImpactEvidence.ImpactStartEvidence> cappedStarts =
                EvidenceLimits.first(starts, EvidenceLimits.MAX_ROOTS);
        List<ImpactEvidence.ImpactedEntityEvidence> cappedImpacted =
                EvidenceLimits.first(impacted, EvidenceLimits.MAX_IMPACTED_ENTITIES);
        List<ImpactEvidence.ImpactPathEvidence> cappedPaths =
                EvidenceLimits.first(paths, EvidenceLimits.MAX_IMPACT_PATHS_PACKET);

        boolean truncated = impact.truncated() || localTruncated;
        return new ImpactEvidence(
                cappedStarts,
                cappedImpacted,
                cappedPaths,
                impact.maxDepth(),
                impact.nodeCount(),
                impact.pathCount(),
                truncated,
                EvidenceLimits.text(impact.truncationReason()),
                impact.cycleEncountered(),
                impact.snapshotObservedAt(),
                impact.entityCount(),
                impact.relationshipCount(),
                impact.limits() == null
                        ? null
                        : new ImpactEvidence.ImpactLimitsEvidence(
                                impact.limits().maxStarts(),
                                impact.limits().maxDepthAllowed(),
                                impact.limits().maxNodes(),
                                impact.limits().maxPaths()),
                EvidenceLimits.text(impact.note()));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
