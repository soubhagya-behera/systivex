package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.telemetry.TelemetryConnector;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeObservationEvidenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void freshConnectorOwnedRuntimeIsCurrentAndAllowlisted() {
        Map<String, Long> counters = new HashMap<>();
        counters.put("payment.authorization.success", 4L);
        counters.put("checkout.success", 7L);
        counters.put("secret.counter", 99L);
        SystemEntity entity = ownedEntity(NOW.minus(Duration.ofMinutes(5)), true, "UP", counters,
                List.of("trace presence not collected"));

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
        RuntimeObservationEvidence content = item.content();
        assertThat(content.healthAvailable()).isTrue();
        assertThat(content.healthStatus()).isEqualTo("UP");
        assertThat(content.observedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
        assertThat(content.counters().keySet())
                .containsExactly("checkout.success", "payment.authorization.success");
        assertThat(content.counters()).doesNotContainKey("secret.counter");
        assertThat(item.timestamps()).hasSize(1);
    }

    @Test
    void exactlyThirtyMinutesOldIsStillFresh() {
        SystemEntity entity = ownedEntity(
                NOW.minus(Duration.ofMinutes(30)), true, "UP", Map.of(), List.of());

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
    }

    @Test
    void olderThanThirtyMinutesIsStaleButKeepsEvidence() {
        SystemEntity entity = ownedEntity(
                NOW.minus(Duration.ofMinutes(31)), true, "UP", Map.of(), List.of());

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.qualification()).isEqualTo(EvidenceQualification.STALE);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.limitations()).anyMatch(value -> value.contains("stale"));
        assertThat(item.content().healthAvailable()).isTrue();
    }

    @Test
    void futureTimestampIsStale() {
        SystemEntity entity = ownedEntity(
                NOW.plus(Duration.ofMinutes(1)), true, "UP", Map.of(), List.of());

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.qualification()).isEqualTo(EvidenceQualification.STALE);
    }

    @Test
    void invalidTimestampIsStaleAndExplicit() {
        SystemEntity entity = ownedEntity(null, true, "UP", Map.of(), List.of());
        Object runtime = entity.getMetadata().get(TelemetryConnector.RUNTIME_KEY);
        ((Map<String, Object>) runtime).put("observedAt", "not-a-timestamp");

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.content().observedAt()).isNull();
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.STALE);
        assertThat(item.limitations()).anyMatch(value -> value.contains("invalid observedAt"));
        assertThat(item.limitations()).anyMatch(value -> value.contains("stale"));
    }

    @Test
    void unavailableHealthIsUncertainAndUnavailable() {
        SystemEntity entity = ownedEntity(
                NOW.minus(Duration.ofMinutes(5)), false, "DOWN", Map.of(), List.of());

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.UNCERTAIN);
        assertThat(item.limitations()).anyMatch(value -> value.contains("health unavailable"));
    }

    @Test
    void unownedRuntimeIsRejectedEvenWhenShapeLooksValid() {
        SystemEntity entity = new SystemEntity(
                SystemEntityType.SERVICE,
                "checkout",
                "target-service:checkout",
                "prod",
                new HashMap<>(Map.of(
                        "managedBy", "someone-else",
                        TelemetryConnector.RUNTIME_KEY,
                        runtime(NOW.minus(Duration.ofMinutes(1)), true, "UP", Map.of(), List.of()))));

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.UNCERTAIN);
        assertThat(item.content().counters()).isEmpty();
        assertThat(item.limitations()).anyMatch(value -> value.contains("not owned"));
    }

    @Test
    void runtimeWithWrongConnectorMarkerIsRejected() {
        SystemEntity entity = ownedEntity(
                NOW.minus(Duration.ofMinutes(1)), true, "UP", Map.of(), List.of());
        Object runtime = entity.getMetadata().get(TelemetryConnector.RUNTIME_KEY);
        ((Map<String, Object>) runtime).put("managedBy", "other-connector");

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.limitations()).anyMatch(value -> value.contains("not owned"));
    }

    @Test
    void limitationsAreCappedAndExplicitlyFlagged() {
        List<String> limitations = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            limitations.add("limitation-" + i);
        }
        SystemEntity entity = ownedEntity(
                NOW.minus(Duration.ofMinutes(5)), true, "UP", Map.of(), limitations);

        EvidenceItem<RuntimeObservationEvidence> item =
                RuntimeObservationEvidenceAdapter.adaptItem(entity, NOW);

        assertThat(item.content().limitations()).hasSize(EvidenceLimits.MAX_LIMITATIONS);
        assertThat(item.content().limitations().get(EvidenceLimits.MAX_LIMITATIONS - 1))
                .contains("capped");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
    }

    @Test
    void missingRuntimeReturnsNoItemAndMissingEntityReturnsNull() {
        SystemEntity plain = new SystemEntity(
                SystemEntityType.SERVICE, "svc", "svc-1", "prod", Map.of());
        assertThat(RuntimeObservationEvidenceAdapter.adaptItem(plain, NOW)).isNull();
        assertThat(RuntimeObservationEvidenceAdapter.adaptItem(null, NOW)).isNull();
    }

    private static SystemEntity ownedEntity(
            Instant observedAt,
            boolean healthAvailable,
            String healthStatus,
            Map<String, Long> counters,
            List<String> limitations) {
        return new SystemEntity(
                SystemEntityType.SERVICE,
                "checkout",
                "target-service:checkout",
                "prod",
                new HashMap<>(Map.of(
                        "managedBy", "repository-connector",
                        TelemetryConnector.RUNTIME_KEY,
                        runtime(observedAt, healthAvailable, healthStatus, counters, limitations))));
    }

    private static Map<String, Object> runtime(
            Instant observedAt,
            boolean healthAvailable,
            String healthStatus,
            Map<String, Long> counters,
            List<String> limitations) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("available", healthAvailable);
        health.put("status", healthStatus);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("managedBy", TelemetryConnector.CONNECTOR_ID);
        runtime.put("connectorVersion", TelemetryConnector.CONNECTOR_VERSION);
        runtime.put("observedAt", observedAt == null ? null : observedAt.toString());
        runtime.put("health", health);
        runtime.put("counters", counters);
        runtime.put("limitations", limitations);
        return runtime;
    }
}
