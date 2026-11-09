package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.RiskLevel;
import com.soubhagya.systivex.intelligence.RiskResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RiskEvidenceAdapter implements EvidenceSource<RiskResponse> {

    public RiskEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(RiskResponse risk) {
        EvidenceItem<RiskEvidence> item = adaptItem(risk);
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<RiskEvidence> adaptItem(RiskResponse risk) {
        RiskEvidence content = toContent(risk);
        if (content == null) {
            return null;
        }

        List<String> limitations = new ArrayList<>();
        if (risk.note() != null && !risk.note().isBlank()) {
            limitations.add(EvidenceLimits.text(risk.note()));
        }
        if (EvidenceLimits.exceeds(risk.note(), EvidenceLimits.MAX_TEXT_LENGTH)) {
            limitations.add("Risk note was truncated to "
                    + EvidenceLimits.MAX_TEXT_LENGTH + " characters.");
        }

        int sourceRisks = nonNullCount(risk.risks());
        int omittedStarts = Math.max(0, size(risk.startedFrom()) - content.startedFrom().size());
        if (omittedStarts > 0) {
            limitations.add("Risk starts capped at " + EvidenceLimits.MAX_ROOTS
                    + "; " + omittedStarts + " omitted.");
        }
        int omittedRisks = Math.max(0, sourceRisks - content.risks().size());
        if (omittedRisks > 0) {
            limitations.add("Risk entries capped at " + EvidenceLimits.MAX_RISK_ENTRIES
                    + "; server reported " + sourceRisks + " entries; "
                    + omittedRisks + " omitted.");
        }

        int omittedReasons = 0;
        int truncatedEntries = 0;
        for (RiskEvidence.RiskEntryEvidence entry : content.risks()) {
            omittedReasons += entry.omittedReasonCount();
            if (entry.truncated()) {
                truncatedEntries++;
            }
        }
        if (truncatedEntries > 0) {
            limitations.add("Risk reasons truncated for " + truncatedEntries
                    + " entries (cap " + EvidenceLimits.MAX_RISK_REASONS
                    + " reasons and " + EvidenceLimits.MAX_TEXT_LENGTH
                    + " characters each).");
        }

        EvidenceLimits.BoundedText bounded =
                EvidenceLimits.textList(
                        limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        List<String> finalLimitations = new ArrayList<>(bounded.values());
        boolean truncated = content.truncated() || bounded.truncated();
        if (bounded.truncated()) {
            String summary = "Risk limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted";
            if (finalLimitations.size() >= EvidenceLimits.MAX_LIMITATIONS) {
                finalLimitations.set(EvidenceLimits.MAX_LIMITATIONS - 1, summary);
            } else {
                finalLimitations.add(summary);
            }
        }

        EvidenceQualification qualification;
        if (truncated) {
            qualification = EvidenceQualification.TRUNCATED;
        } else if (content.risks().stream().anyMatch(RiskEvidence.RiskEntryEvidence::uncertain)) {
            qualification = EvidenceQualification.UNCERTAIN;
        } else if (content.risks().stream().anyMatch(RiskEvidence.RiskEntryEvidence::stale)) {
            qualification = EvidenceQualification.STALE;
        } else {
            qualification = EvidenceQualification.CURRENT;
        }

        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.DETERMINISTIC_FINDING,
                "risk.assessment",
                "risk:" + size(risk.startedFrom()) + " roots",
                risk.taxonomyVersion(),
                risk.snapshotObservedAt() == null
                        ? List.of()
                        : List.of(new EvidenceTimestamp(
                                "snapshotObservedAt", risk.snapshotObservedAt())),
                content,
                finalLimitations,
                qualification,
                truncated ? EvidenceSourceStatus.TRUNCATED : EvidenceSourceStatus.AVAILABLE,
                content.startedFrom().size() + content.risks().size(),
                omittedStarts + omittedRisks + omittedReasons);
    }

    public static RiskEvidence toContent(RiskResponse risk) {
        if (risk == null) {
            return null;
        }

        List<ImpactEvidence.ImpactStartEvidence> starts = new ArrayList<>();
        for (com.soubhagya.systivex.intelligence.ImpactResponse.StartEntity start :
                safe(risk.startedFrom())) {
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

        List<RiskEvidence.RiskEntryEvidence> entries = new ArrayList<>();
        for (RiskResponse.RiskEntry entry : safe(risk.risks())) {
            if (entry == null) {
                continue;
            }
            List<String> rawReasons = new ArrayList<>();
            for (String reason : safe(entry.reasons())) {
                if (reason != null) {
                    rawReasons.add(reason);
                }
            }
            rawReasons.sort(String::compareTo);
            EvidenceLimits.BoundedText reasons = EvidenceLimits.textList(
                    rawReasons, EvidenceLimits.MAX_RISK_REASONS, EvidenceLimits.MAX_TEXT_LENGTH);
            int omittedReasons = Math.max(0, rawReasons.size() - reasons.values().size());
            entries.add(new RiskEvidence.RiskEntryEvidence(
                    entry.id(),
                    entry.type(),
                    EvidenceLimits.text(entry.name()),
                    EvidenceLimits.text(entry.externalRef()),
                    entry.depth(),
                    EvidenceLimits.text(entry.viaStart()),
                    entry.level(),
                    reasons.values(),
                    entry.uncertain(),
                    entry.stale(),
                    reasons.truncated(),
                    omittedReasons));
        }
        entries.sort(Comparator.comparingInt(
                        (RiskEvidence.RiskEntryEvidence entry) -> severityRank(entry.level()))
                .thenComparing(entry -> entry.externalRef() == null ? "" : entry.externalRef())
                .thenComparing(entry -> entry.id() == null ? "" : entry.id().toString()));

        boolean localTruncated = starts.size() > EvidenceLimits.MAX_ROOTS
                || entries.size() > EvidenceLimits.MAX_RISK_ENTRIES
                || entries.stream().anyMatch(RiskEvidence.RiskEntryEvidence::truncated)
                || EvidenceLimits.exceeds(risk.note(), EvidenceLimits.MAX_TEXT_LENGTH);
        List<RiskEvidence.RiskEntryEvidence> capped =
                EvidenceLimits.first(entries, EvidenceLimits.MAX_RISK_ENTRIES);

        return new RiskEvidence(
                EvidenceLimits.first(starts, EvidenceLimits.MAX_ROOTS),
                capped,
                risk.maxDepth(),
                risk.taxonomyVersion(),
                risk.snapshotObservedAt(),
                risk.entityCount(),
                risk.relationshipCount(),
                localTruncated,
                EvidenceLimits.text(risk.note()));
    }

    private static int severityRank(RiskLevel level) {
        if (level == null) {
            return 4;
        }
        return switch (level) {
            case HIGH -> 0;
            case UNKNOWN -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
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
}
