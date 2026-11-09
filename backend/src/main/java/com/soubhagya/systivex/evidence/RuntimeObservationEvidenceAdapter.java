package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.RiskTaxonomy;
import com.soubhagya.systivex.telemetry.TelemetryConnector;
import com.soubhagya.systivex.telemetry.TelemetryObservationService;
import com.soubhagya.systivex.twin.model.SystemEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class RuntimeObservationEvidenceAdapter implements EvidenceSource<SystemEntity> {

    private static final Set<String> ALLOWED_COUNTERS = Set.of(
            "checkout.success",
            "checkout.failure",
            "inventory.reservation.success",
            "inventory.reservation.rejected",
            "payment.authorization.success",
            "payment.authorization.declined");

    public RuntimeObservationEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(SystemEntity entity) {
        EvidenceItem<RuntimeObservationEvidence> item = adaptItem(entity, Instant.now());
        return item == null ? List.of() : List.of(item);
    }

    public static EvidenceItem<RuntimeObservationEvidence> adaptItem(SystemEntity entity) {
        return adaptItem(entity, Instant.now());
    }

    public static EvidenceItem<RuntimeObservationEvidence> adaptItem(
            SystemEntity entity, Instant now) {
        if (entity == null || entity.getMetadata() == null) {
            return null;
        }
        Object runtimeObject = entity.getMetadata().get(TelemetryConnector.RUNTIME_KEY);
        if (!(runtimeObject instanceof Map<?, ?> runtime)) {
            return null;
        }

        String externalRef = EvidenceLimits.text(entity.getExternalRef());
        String scope = entity.getExternalRef() == null || entity.getExternalRef().isBlank()
                ? "unknown"
                : entity.getExternalRef();
        EvidenceReference ref = EvidenceReference.of(1);

        boolean connectorOwned = TelemetryObservationService.isConnectorOwned(entity)
                && TelemetryConnector.CONNECTOR_ID.equals(runtime.get("managedBy"));
        if (!connectorOwned) {
            List<String> limitations = List.of(
                    "runtime metadata is not owned by the telemetry connector;"
                            + " it is not used as runtime evidence");
            RuntimeObservationEvidence content = new RuntimeObservationEvidence(
                    externalRef, false, null, null, Map.of(), limitations);
            return new EvidenceItem<>(
                    ref,
                    EvidenceCategory.OBSERVED_FACT,
                    "twin.runtime",
                    scope,
                    null,
                    List.of(),
                    content,
                    limitations,
                    EvidenceQualification.UNCERTAIN,
                    EvidenceSourceStatus.UNAVAILABLE,
                    1,
                    0);
        }

        List<String> limitations = new ArrayList<>();
        Object rawLimitations = runtime.get("limitations");
        if (rawLimitations instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    limitations.add(String.valueOf(item));
                }
            }
        }

        boolean healthAvailable = false;
        String healthStatus = null;
        Object healthObject = runtime.get("health");
        if (healthObject instanceof Map<?, ?> health) {
            healthAvailable = Boolean.TRUE.equals(health.get("available"));
            Object status = health.get("status");
            healthStatus = status == null ? null : EvidenceLimits.text(String.valueOf(status));
            if (!healthAvailable) {
                limitations.add("health unavailable at last observation");
            }
        } else {
            limitations.add("health unavailable at last observation");
        }

        Instant observedAt = null;
        Object observed = runtime.get("observedAt");
        if (observed instanceof String text) {
            try {
                observedAt = Instant.parse(text.trim());
            } catch (RuntimeException e) {
                limitations.add("invalid observedAt timestamp");
            }
        } else {
            limitations.add("missing observedAt timestamp");
        }

        boolean stale = isStale(observedAt, now);
        if (stale) {
            limitations.add("runtime evidence is stale (older than "
                    + RiskTaxonomy.STALE_AFTER.toMinutes() + " minutes or not a valid past time)");
        }

        Map<String, Long> counters = new TreeMap<>();
        Object countersObject = runtime.get("counters");
        if (countersObject instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!ALLOWED_COUNTERS.contains(key)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    counters.put(key, number.longValue());
                }
            }
        }

        limitations.sort(String::compareTo);
        EvidenceLimits.BoundedText bounded =
                EvidenceLimits.textList(
                        limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH);
        List<String> finalLimitations = new ArrayList<>(bounded.values());
        if (bounded.truncated()) {
            String summary = "Runtime limitations capped at "
                    + EvidenceLimits.MAX_LIMITATIONS + "; additional limitations omitted";
            if (finalLimitations.size() >= EvidenceLimits.MAX_LIMITATIONS) {
                finalLimitations.set(EvidenceLimits.MAX_LIMITATIONS - 1, summary);
            } else {
                finalLimitations.add(summary);
            }
        }

        RuntimeObservationEvidence content = new RuntimeObservationEvidence(
                externalRef,
                healthAvailable,
                healthStatus,
                observedAt,
                counters,
                finalLimitations);

        EvidenceQualification qualification = stale
                ? EvidenceQualification.STALE
                : (healthAvailable ? EvidenceQualification.CURRENT : EvidenceQualification.UNCERTAIN);
        EvidenceSourceStatus status;
        if (bounded.truncated()) {
            status = EvidenceSourceStatus.TRUNCATED;
        } else {
            status = healthAvailable
                    ? EvidenceSourceStatus.AVAILABLE
                    : EvidenceSourceStatus.UNAVAILABLE;
        }

        return new EvidenceItem<>(
                ref,
                EvidenceCategory.OBSERVED_FACT,
                "twin.runtime",
                scope,
                null,
                observedAt == null
                        ? List.of()
                        : List.of(new EvidenceTimestamp("observedAt", observedAt)),
                content,
                finalLimitations,
                qualification,
                status,
                1,
                0);
    }

    static boolean isStale(Instant observedAt, Instant now) {
        if (observedAt == null || now == null) {
            return true;
        }
        Duration age = Duration.between(observedAt, now);
        if (age.isNegative()) {
            return true;
        }
        return age.compareTo(RiskTaxonomy.STALE_AFTER) > 0;
    }
}
