package com.soubhagya.systivex.intelligence;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Impact query validation shared by analyze, simulation, and governed
 * assessment (Phase 5 semantics, extracted from
 * {@link ImpactAnalysisService}).
 *
 * <p>Pure and repository-free: callers supply identifier resolution, so the
 * live query resolves against the repositories while assessment resolves
 * against its captured in-memory snapshot. Both paths share one validation
 * core — blank/oversized input fails 400, unknown identifiers 404 — with
 * identical trimming, bounds, defaults, and deterministic start ordering.
 *
 * <p>Identifier size is bounded before any resolution: one starting
 * identifier may not exceed {@link #MAX_IDENTIFIER_CHARS} characters and the
 * whole list may not exceed {@link #MAX_TOTAL_IDENTIFIER_CHARS} characters in
 * total. Both bounds are measured on the raw input (before trimming), so
 * padding cannot smuggle unbounded text past the check, and both are
 * enforced before the first resolver call — no repository lookup, no graph
 * traversal, and no user-supplied text in the error message.
 */
public final class ImpactQueryValidator {

    static final int MAX_STARTS = 10;

    /**
     * Maximum length of one starting identifier, measured before trimming.
     *
     * <p>512 is the repository's own bound for a twin external reference
     * ({@code CreateSystemEntityRequest.externalRef} carries
     * {@code @Size(max = 512)} and the {@code system_entity.external_ref}
     * column is {@code VARCHAR(512)}). A UUID renders as 36 characters, so
     * every identifier that can resolve is well inside the bound: anything
     * longer could never match a stored row and is rejected up front
     * instead of being echoed into a 404.
     */
    static final int MAX_IDENTIFIER_CHARS = 512;

    /**
     * Maximum total length of the starting-identifier list, measured before
     * trimming.
     *
     * <p>1024 is the repository's existing long-text bound (see
     * {@code @Size(max = 1024)} on the sync root and the decision reason).
     * It keeps the accepted input realistic — ten UUIDs are 360 characters
     * and ten scanned external references stay near 520 — while bounding
     * the total text that reaches the resolver layer and any error message.
     */
    static final int MAX_TOTAL_IDENTIFIER_CHARS = 1024;

    static final int DEFAULT_MAX_DEPTH = 6;
    static final int MAX_DEPTH_ALLOWED = 10;

    static final Set<SystemEntityType> DEFAULT_INCLUDE_TYPES =
            EnumSet.of(
                    SystemEntityType.SERVICE,
                    SystemEntityType.API,
                    SystemEntityType.DATABASE,
                    SystemEntityType.TABLE);

    private ImpactQueryValidator() {}

    /** Validated, resolved, and sorted query inputs. */
    public record ParsedInputs(
            List<SystemEntity> starts, int maxDepth, Set<SystemEntityType> includeTypes) {}

    /** Resolves one UUID-or-external-reference identifier; 404 on unknown. */
    @FunctionalInterface
    public interface EntityLookup {
        SystemEntity lookup(String raw);
    }

    /**
     * Validates raw query input and resolves every starting entity through
     * the caller-supplied lookup. Blank/oversized input fails with
     * {@link IllegalArgumentException} (400) before the first lookup — too
     * many starts, an identifier longer than {@link #MAX_IDENTIFIER_CHARS}
     * characters, or more than {@link #MAX_TOTAL_IDENTIFIER_CHARS} characters
     * in total; unknown identifiers fail with {@link EntityNotFoundException}
     * (404).
     */
    public static ParsedInputs parseInputs(
            List<String> rawFrom,
            Integer rawMaxDepth,
            List<String> rawIncludeTypes,
            EntityLookup lookup) {
        return parseCore(rawFrom, rawMaxDepth, rawIncludeTypes, lookup);
    }

    /**
     * Snapshot-consistent variant: identical validation, but every
     * identifier resolves against the caller-supplied in-memory snapshot
     * only — no repository access, so simulation, risk, policy, and the
     * fingerprint describe the same captured state. UUID first, then
     * external reference, 404 on unknown.
     */
    public static ParsedInputs parseSnapshotInputs(
            Map<UUID, SystemEntity> entitiesById,
            List<String> rawFrom,
            Integer rawMaxDepth,
            List<String> rawIncludeTypes) {
        return parseCore(rawFrom, rawMaxDepth, rawIncludeTypes,
                raw -> snapshotLookup(entitiesById, raw));
    }

    /**
     * Snapshot-consistent variant for a root set the caller derived itself,
     * not one it supplied as query input.
     *
     * <p>{@link #MAX_STARTS} is a query-input bound: it caps the
     * caller-supplied {@code from} list of the impact query (D23). A
     * governed proposal derives its roots from its own operations — the
     * entities the change touches — so the same cap must not reject a
     * proposal that was accepted at creation: the proposal limit allows
     * 100 operations, which can touch far more than ten entities, and a
     * proposal that cannot be assessed stays in {@code DRAFT} forever, so
     * it can never be submitted or approved. The caller-facing aggregate
     * raw-identifier bound ({@link #MAX_TOTAL_IDENTIFIER_CHARS}) is likewise
     * query input and does not apply here; the per-identifier safety bound
     * still does. The derived set is bounded by the proposal's own operation
     * limit, and the traversal keeps its node and path caps, so the analysis
     * stays bounded either way.
     */
    public static ParsedInputs parseSnapshotRoots(
            Map<UUID, SystemEntity> entitiesById,
            List<String> rootIds,
            Integer rawMaxDepth,
            List<String> rawIncludeTypes) {
        return parseCore(rootIds, rawMaxDepth, rawIncludeTypes,
                raw -> snapshotLookup(entitiesById, raw), Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static ParsedInputs parseCore(
            List<String> rawFrom,
            Integer rawMaxDepth,
            List<String> rawIncludeTypes,
            EntityLookup lookup) {
        return parseCore(rawFrom, rawMaxDepth, rawIncludeTypes, lookup, MAX_STARTS,
                MAX_TOTAL_IDENTIFIER_CHARS);
    }

    private static ParsedInputs parseCore(
            List<String> rawFrom,
            Integer rawMaxDepth,
            List<String> rawIncludeTypes,
            EntityLookup lookup,
            int maxStarts,
            int maxTotalChars) {
        if (rawFrom == null || rawFrom.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one starting entity is required (query parameter 'from')");
        }
        List<String> cleaned = new ArrayList<>();
        int totalChars = 0;
        for (String raw : rawFrom) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "Starting entity identifiers must not be blank");
            }
            if (raw.length() > MAX_IDENTIFIER_CHARS) {
                throw new IllegalArgumentException(
                        "Starting entity identifiers must not exceed "
                                + MAX_IDENTIFIER_CHARS + " characters");
            }
            totalChars += raw.length();
            if (totalChars > maxTotalChars) {
                throw new IllegalArgumentException(
                        "Starting entity identifiers must not exceed "
                                + maxTotalChars + " characters in total");
            }
            cleaned.add(raw.trim());
        }
        if (cleaned.size() > maxStarts) {
            throw new IllegalArgumentException(
                    "At most " + maxStarts + " starting entities are supported");
        }
        int maxDepth = rawMaxDepth == null ? DEFAULT_MAX_DEPTH : rawMaxDepth;
        if (maxDepth < 1 || maxDepth > MAX_DEPTH_ALLOWED) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and " + MAX_DEPTH_ALLOWED);
        }
        Set<SystemEntityType> includeTypes = parseIncludeTypes(rawIncludeTypes);

        List<SystemEntity> starts = new ArrayList<>();
        for (String raw : cleaned) {
            starts.add(lookup.lookup(raw));
        }
        // Deterministic start order: externalRef first, then id.
        starts.sort(
                Comparator.comparing(
                                (SystemEntity e) -> e.getExternalRef(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(e -> e.getId().toString()));

        return new ParsedInputs(starts, maxDepth, includeTypes);
    }

    private static SystemEntity snapshotLookup(
            Map<UUID, SystemEntity> entitiesById, String raw) {
        UUID id = tryParseUuid(raw.trim());
        if (id != null && entitiesById.containsKey(id)) {
            return entitiesById.get(id);
        }
        for (SystemEntity entity : entitiesById.values()) {
            if (raw.trim().equals(entity.getExternalRef())) {
                return entity;
            }
        }
        throw new EntityNotFoundException("SystemEntity not found: " + raw);
    }

    private static Set<SystemEntityType> parseIncludeTypes(List<String> rawIncludeTypes) {
        if (rawIncludeTypes == null || rawIncludeTypes.isEmpty()) {
            return EnumSet.copyOf(DEFAULT_INCLUDE_TYPES);
        }
        Set<SystemEntityType> parsed = EnumSet.noneOf(SystemEntityType.class);
        for (String raw : rawIncludeTypes) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "includeTypes values must not be blank");
            }
            try {
                parsed.add(SystemEntityType.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown entity type: " + raw.trim());
            }
        }
        return parsed;
    }

    private static UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
