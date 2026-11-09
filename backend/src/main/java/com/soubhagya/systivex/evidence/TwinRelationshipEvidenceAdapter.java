package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.twin.api.SystemRelationshipResponse;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TwinRelationshipEvidenceAdapter implements EvidenceSource<SystemRelationship> {

    private static final Comparator<SystemRelationship> ORDER =
            Comparator.comparing(
                            (SystemRelationship relationship) ->
                                    relationship.getSource() == null
                                                    || relationship.getSource().getId() == null
                                            ? null
                                            : relationship.getSource().getId().toString(),
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(
                            relationship ->
                                    relationship.getTarget() == null
                                                    || relationship.getTarget().getId() == null
                                            ? null
                                            : relationship.getTarget().getId().toString(),
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(
                            relationship ->
                                    relationship.getRelationshipType() == null
                                            ? null
                                            : relationship.getRelationshipType().name(),
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(
                            relationship ->
                                    relationship.getId() == null
                                            ? null
                                            : relationship.getId().toString(),
                            Comparator.nullsLast(String::compareTo));

    public TwinRelationshipEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(SystemRelationship relationship) {
        EvidenceItem<TwinRelationshipEvidence> item =
                mapItem(relationship, EvidenceReference.of(1));
        return item == null ? List.of() : List.of(item);
    }

    public List<EvidenceItem<?>> adaptAll(List<SystemRelationship> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return List.of();
        }
        List<SystemRelationship> sorted = new ArrayList<>(relationships);
        sorted.removeIf(Objects::isNull);
        sorted.sort(ORDER);
        int included = Math.min(sorted.size(), EvidenceLimits.MAX_TWIN_RELATIONSHIPS);
        boolean truncated = sorted.size() > included;
        List<EvidenceItem<?>> items = new ArrayList<>();
        for (int i = 0; i < included; i++) {
            items.add(mapItem(sorted.get(i), EvidenceReference.of(i + 1)));
        }
        if (truncated && !items.isEmpty()) {
            int omitted = sorted.size() - included;
            EvidenceItem<?> last = items.get(items.size() - 1);
            items.set(
                    items.size() - 1,
                    last.withTruncation(
                            EvidenceSourceStatus.TRUNCATED,
                            EvidenceQualification.TRUNCATED,
                            List.of("Twin relationship evidence capped at "
                                    + EvidenceLimits.MAX_TWIN_RELATIONSHIPS + "; "
                                    + omitted + " records omitted."),
                            included,
                            omitted));
        }
        return List.copyOf(items);
    }

    public static EvidenceItem<TwinRelationshipEvidence> adaptResponse(
            SystemRelationshipResponse response) {
        if (response == null) {
            return null;
        }
        return item(
                EvidenceReference.of(1),
                response.id() == null ? "unknown" : response.id().toString(),
                new TwinRelationshipEvidence(
                        response.id(),
                        response.sourceId(),
                        response.targetId(),
                        response.type(),
                        response.createdAt()));
    }

    private static EvidenceItem<TwinRelationshipEvidence> mapItem(
            SystemRelationship relationship, EvidenceReference ref) {
        if (relationship == null) {
            return null;
        }
        return item(
                ref,
                relationship.getId() == null ? "unknown" : relationship.getId().toString(),
                new TwinRelationshipEvidence(
                        relationship.getId(),
                        relationship.getSource() == null
                                ? null
                                : relationship.getSource().getId(),
                        relationship.getTarget() == null
                                ? null
                                : relationship.getTarget().getId(),
                        relationship.getRelationshipType(),
                        relationship.getCreatedAt()));
    }

    private static EvidenceItem<TwinRelationshipEvidence> item(
            EvidenceReference ref, String scope, TwinRelationshipEvidence content) {
        return new EvidenceItem<>(
                ref,
                EvidenceCategory.OBSERVED_FACT,
                "twin.relationship",
                scope,
                null,
                content.createdAt() == null
                        ? List.of()
                        : List.of(new EvidenceTimestamp("createdAt", content.createdAt())),
                content,
                List.of(),
                EvidenceQualification.CURRENT,
                EvidenceSourceStatus.AVAILABLE,
                1,
                0);
    }
}
