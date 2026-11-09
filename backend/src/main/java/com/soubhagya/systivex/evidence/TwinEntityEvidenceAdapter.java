package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.twin.api.SystemEntityResponse;
import com.soubhagya.systivex.twin.model.SystemEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TwinEntityEvidenceAdapter implements EvidenceSource<SystemEntity> {

    private static final Comparator<SystemEntity> ORDER =
            Comparator.comparing(
                            (SystemEntity entity) -> entity.getExternalRef(),
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(
                            entity -> entity.getId() == null ? "" : entity.getId().toString());

    public TwinEntityEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(SystemEntity entity) {
        EvidenceItem<TwinEntityEvidence> item = mapItem(entity, EvidenceReference.of(1));
        return item == null ? List.of() : List.of(item);
    }

    public List<EvidenceItem<?>> adaptAll(List<SystemEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<SystemEntity> sorted = new ArrayList<>(entities);
        sorted.removeIf(Objects::isNull);
        sorted.sort(ORDER);
        int included = Math.min(sorted.size(), EvidenceLimits.MAX_TWIN_ENTITIES);
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
                            List.of("Twin entity evidence capped at "
                                    + EvidenceLimits.MAX_TWIN_ENTITIES + "; "
                                    + omitted + " records omitted."),
                            included,
                            omitted));
        }
        return List.copyOf(items);
    }

    public static EvidenceItem<TwinEntityEvidence> adaptResponse(SystemEntityResponse response) {
        if (response == null) {
            return null;
        }
        return item(
                EvidenceReference.of(1),
                response.id() == null ? "unknown" : response.id().toString(),
                new TwinEntityEvidence(
                        response.id(),
                        response.type(),
                        EvidenceLimits.text(response.name()),
                        EvidenceLimits.text(response.externalRef()),
                        EvidenceLimits.text(response.environment()),
                        response.createdAt(),
                        response.updatedAt()));
    }

    private static EvidenceItem<TwinEntityEvidence> mapItem(
            SystemEntity entity, EvidenceReference ref) {
        if (entity == null) {
            return null;
        }
        return item(
                ref,
                entity.getId() == null ? "unknown" : entity.getId().toString(),
                new TwinEntityEvidence(
                        entity.getId(),
                        entity.getType(),
                        EvidenceLimits.text(entity.getName()),
                        EvidenceLimits.text(entity.getExternalRef()),
                        EvidenceLimits.text(entity.getEnvironment()),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()));
    }

    private static EvidenceItem<TwinEntityEvidence> item(
            EvidenceReference ref, String scope, TwinEntityEvidence content) {
        List<EvidenceTimestamp> timestamps = new ArrayList<>();
        if (content.createdAt() != null) {
            timestamps.add(new EvidenceTimestamp("createdAt", content.createdAt()));
        }
        if (content.updatedAt() != null) {
            timestamps.add(new EvidenceTimestamp("updatedAt", content.updatedAt()));
        }
        return new EvidenceItem<>(
                ref,
                EvidenceCategory.OBSERVED_FACT,
                "twin.entity",
                scope,
                null,
                timestamps,
                content,
                List.of(),
                EvidenceQualification.CURRENT,
                EvidenceSourceStatus.AVAILABLE,
                1,
                0);
    }
}
