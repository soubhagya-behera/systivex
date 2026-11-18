package com.soubhagya.systivex.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Validator unit tests: query validation (400/404 contracts), defaults,
 * deterministic ordering, and snapshot resolution — all without a database.
 */
class ImpactQueryValidatorTest {

    private static final UUID ID_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Map<UUID, SystemEntity> snapshot() {
        Map<UUID, SystemEntity> entities = new LinkedHashMap<>();
        entities.put(ID_A, entity(ID_A, "svc:a"));
        entities.put(ID_B, entity(ID_B, "svc:b"));
        return entities;
    }

    private static SystemEntity entity(UUID id, String ref) {
        SystemEntity entity =
                new SystemEntity(SystemEntityType.SERVICE, ref, ref, "dev", Map.of());
        try {
            var idField = SystemEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    @Test
    void nullBlankAndOversizedFromRejected() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThatThrownBy(() -> ImpactQueryValidator.parseSnapshotInputs(entities, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImpactQueryValidator.parseSnapshotInputs(entities, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("  "), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> oversized = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            oversized.add("svc:a");
        }
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(entities, oversized, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxDepthBoundsAndDefaults() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:a"), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:a"), 11, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ImpactQueryValidator.parseSnapshotInputs(entities, List.of("svc:a"), null, null)
                        .maxDepth())
                .isEqualTo(6);
    }

    @Test
    void unknownAndBlankIncludeTypesRejected() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:a"), null, List.of("NOPE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:a"), null, List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void includeTypesDefaultAndCaseInsensitive() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThat(ImpactQueryValidator.parseSnapshotInputs(entities, List.of("svc:a"), null, null)
                        .includeTypes())
                .containsExactlyInAnyOrder(
                        SystemEntityType.SERVICE,
                        SystemEntityType.API,
                        SystemEntityType.DATABASE,
                        SystemEntityType.TABLE);
        assertThat(ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:a"), null, List.of("service"))
                        .includeTypes())
                .containsExactly(SystemEntityType.SERVICE);
    }

    @Test
    void snapshotUnknownIdentifierIs404() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of("svc:ghost"), null, null))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of(UUID.randomUUID().toString()), null, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void snapshotUuidAndRefResolveIdentically() {
        Map<UUID, SystemEntity> entities = snapshot();
        ImpactQueryValidator.ParsedInputs byRef =
                ImpactQueryValidator.parseSnapshotInputs(entities, List.of("svc:a"), null, null);
        ImpactQueryValidator.ParsedInputs byUuid =
                ImpactQueryValidator.parseSnapshotInputs(
                        entities, List.of(ID_A.toString()), null, null);
        assertThat(byUuid.starts().get(0).getId()).isEqualTo(ID_A);
        assertThat(byRef.starts().get(0).getId()).isEqualTo(byUuid.starts().get(0).getId());
    }

    @Test
    void snapshotStartsSortedAndTrimmed() {
        Map<UUID, SystemEntity> entities = snapshot();
        ImpactQueryValidator.ParsedInputs reversed =
                ImpactQueryValidator.parseSnapshotInputs(
                        entities, List.of("  svc:b ", "svc:a"), null, null);
        assertThat(reversed.starts().stream().map(SystemEntity::getExternalRef).toList())
                .containsExactly("svc:a", "svc:b");
    }

    @Test
    void identifierAtMaximumLengthIsAccepted() {
        String ref = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS);
        Map<UUID, SystemEntity> entities = new LinkedHashMap<>();
        entities.put(ID_A, entity(ID_A, ref));

        ImpactQueryValidator.ParsedInputs parsed =
                ImpactQueryValidator.parseSnapshotInputs(entities, List.of(ref), null, null);

        assertThat(parsed.starts()).hasSize(1);
        assertThat(parsed.starts().get(0).getExternalRef()).isEqualTo(ref);
    }

    @Test
    void identifierExceedingMaximumLengthIsRejectedWithoutResolution() {
        String ref = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS + 1);
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(
                        () ->
                                ImpactQueryValidator.parseInputs(
                                        List.of(ref), null, null, recordingLookup(seen)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not exceed " + ImpactQueryValidator.MAX_IDENTIFIER_CHARS + " characters");
        assertThat(seen).isEmpty();
    }

    @Test
    void paddedIdentifierOverRawLimitIsRejectedWithoutResolution() {
        String ref = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS);
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(
                        () ->
                                ImpactQueryValidator.parseInputs(
                                        List.of("   " + ref), null, null, recordingLookup(seen)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not exceed " + ImpactQueryValidator.MAX_IDENTIFIER_CHARS + " characters");
        assertThat(seen).isEmpty();
    }

    @Test
    void aggregateIdentifierLengthAtLimitIsAccepted() {
        String ref = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS);
        Map<UUID, SystemEntity> entities = new LinkedHashMap<>();
        entities.put(ID_A, entity(ID_A, ref));

        ImpactQueryValidator.ParsedInputs parsed =
                ImpactQueryValidator.parseSnapshotInputs(
                        entities, List.of(ref, ref), null, null);

        assertThat(parsed.starts()).hasSize(2);
    }

    @Test
    void aggregateIdentifierLengthExceedingLimitIsRejectedWithoutResolution() {
        String ref = "r".repeat(400);
        List<String> seen = new ArrayList<>();

        assertThatThrownBy(
                        () ->
                                ImpactQueryValidator.parseInputs(
                                        List.of(ref, ref, ref), null, null, recordingLookup(seen)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not exceed "
                                + ImpactQueryValidator.MAX_TOTAL_IDENTIFIER_CHARS
                                + " characters in total");
        assertThat(seen).isEmpty();
    }

    @Test
    void multipleStartsResolveInDeterministicOrder() {
        Map<UUID, SystemEntity> entities = snapshot();
        List<String> seen = new ArrayList<>();

        ImpactQueryValidator.ParsedInputs parsed =
                ImpactQueryValidator.parseInputs(
                        List.of("  svc:b ", "svc:a"),
                        null,
                        null,
                        raw -> {
                            seen.add(raw);
                            return entities.get(
                                    raw.equals("svc:a") ? ID_A : ID_B);
                        });

        assertThat(seen).containsExactly("svc:b", "svc:a");
        assertThat(parsed.starts().stream().map(SystemEntity::getExternalRef).toList())
                .containsExactly("svc:a", "svc:b");
    }

    @Test
    void uuidAndExternalReferenceStayAccepted() {
        Map<UUID, SystemEntity> entities = snapshot();
        UUID scannedId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String scanned = "target-table:inventory_db.inventory_items";
        entities.put(scannedId, entity(scannedId, scanned));

        assertThat(ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of(ID_A.toString()), null, null)
                        .starts())
                .hasSize(1);
        assertThat(ImpactQueryValidator.parseSnapshotInputs(
                                entities, List.of(scanned), null, null)
                        .starts())
                .hasSize(1);
    }

    @Test
    void liveAndSnapshotPathsRejectOversizedInputIdentically() {
        String ref = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS + 1);
        List<String> seen = new ArrayList<>();

        IllegalArgumentException live =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ImpactQueryValidator.parseInputs(
                                        List.of(ref), null, null, recordingLookup(seen)));
        IllegalArgumentException snapshot =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ImpactQueryValidator.parseSnapshotInputs(
                                        snapshot(), List.of(ref), null, null));

        assertThat(live.getMessage()).isEqualTo(snapshot.getMessage());
        assertThat(seen).isEmpty();
    }

    @Test
    void rejectionMessageDoesNotEchoTheIdentifier() {
        String ref = "secret-marker-" + "r".repeat(600);

        assertThatThrownBy(
                        () ->
                                ImpactQueryValidator.parseInputs(
                                        List.of(ref), null, null, recordingLookup(new ArrayList<>())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-marker")
                .hasMessageNotContaining(ref);
    }

    @Test
    void derivedRootsAreNotCappedByTheQueryStartLimit() {
        Map<UUID, SystemEntity> entities = new LinkedHashMap<>();
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            String ref = "svc:" + i;
            entities.put(id, entity(id, ref));
            refs.add(ref);
        }
        // The query-input cap still bounds a caller-supplied start list.
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(
                                entities, refs, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A root set the caller derived itself is not query input.
        ImpactQueryValidator.ParsedInputs derived =
                ImpactQueryValidator.parseSnapshotRoots(entities, refs, null, null);
        assertThat(derived.starts()).hasSize(12);
        assertThat(derived.maxDepth()).isEqualTo(6);
        assertThat(derived.includeTypes())
                .containsExactlyInAnyOrder(
                        SystemEntityType.SERVICE,
                        SystemEntityType.API,
                        SystemEntityType.DATABASE,
                        SystemEntityType.TABLE);
    }

    @Test
    void derivedRootsKeepTheSameValidationAsQueryInput() {
        Map<UUID, SystemEntity> entities = snapshot();
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of("  "), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of("svc:ghost"), null, null))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of("svc:a"), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of("svc:a"), null, List.of("NOPE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivedRootsExceedCallerAggregateBoundWhileCallerRequestsStayRejected() {
        Map<UUID, SystemEntity> entities = new LinkedHashMap<>();
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            String ref = "svc:" + "r".repeat(100) + i;
            entities.put(id, entity(id, ref));
            refs.add(ref);
        }
        int totalChars = refs.stream().mapToInt(String::length).sum();
        assertThat(refs).hasSizeGreaterThan(ImpactQueryValidator.MAX_STARTS);
        assertThat(totalChars).isGreaterThan(ImpactQueryValidator.MAX_TOTAL_IDENTIFIER_CHARS);

        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotInputs(entities, refs, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ImpactQueryValidator.parseSnapshotRoots(entities, refs, null, null).starts())
                .hasSize(12);

        String oversized = "r".repeat(ImpactQueryValidator.MAX_IDENTIFIER_CHARS + 1);
        assertThatThrownBy(
                        () -> ImpactQueryValidator.parseSnapshotRoots(
                                entities, List.of(oversized), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(oversized);
    }

    /** Records every identifier handed to the resolver and resolves it. */
    private static ImpactQueryValidator.EntityLookup recordingLookup(List<String> seen) {
        return raw -> {
            seen.add(raw);
            return entity(ID_A, raw);
        };
    }
}
