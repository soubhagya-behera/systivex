package com.soubhagya.systivex.proposal.service;

import com.soubhagya.systivex.intelligence.SimulationOperation;
import com.soubhagya.systivex.intelligence.SimulationProposalValidator;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared operation validation for governed proposals (Phase 11 S4).
 *
 * <p>Both draft creation and amendment resolve identifiers with
 * impact-query semantics (UUID first, then external reference, 404 on
 * unknown) and validate through {@link SimulationProposalValidator} with
 * empty analysis roots — root conflicts belong to assessment scope, not
 * to the stored operations. Validation may run against freshly loaded
 * twin rows or a captured snapshot; callers pass the edge-existence check
 * and entity lookup explicitly so assessment stays snapshot-consistent.
 */
public final class ProposalOperations {

    private ProposalOperations() {}

    /** Validated, resolved, canonicalized operations ready to store. */
    public record Prepared(String canonicalJson, String operationsHash,
            SimulationProposalValidator.ResolvedProposal resolved) {}

    public static Prepared prepare(
            List<SimulationOperation> operations,
            SimulationProposalValidator.EdgeExistence existingEdges,
            SimulationProposalValidator.EntityResolver resolver) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("At least one change operation is required");
        }
        SimulationProposalValidator.ResolvedProposal resolved =
                SimulationProposalValidator.validate(operations, Set.of(), existingEdges, resolver);
        String canonical = ProposalCanonical.canonicalize(resolved);
        return new Prepared(canonical, ProposalCanonical.sha256Hex(canonical), resolved);
    }

    /**
     * Edge existence answered by an indexed repository lookup.
     *
     * <p>A proposal proposes at most a bounded number of edges, so only those
     * edges are ever looked up. Each lookup is served by the
     * {@code uq_system_relationship} unique index and materialises no rows,
     * instead of reading the whole relationship table to answer the same
     * questions.
     */
    public static SimulationProposalValidator.EdgeExistence storedEdgeExistence(
            SystemRelationshipRepository relationships) {
        return (sourceId, targetId, type) ->
                relationships.existsBySourceIdAndTargetIdAndRelationshipType(
                        sourceId, targetId, type);
    }

    /** Impact-query identifier semantics over the twin repositories. */
    public static UUID resolve(SystemEntityRepository entities, String raw) {
        String trimmed = raw.trim();
        UUID id = tryParseUuid(trimmed);
        if (id != null) {
            if (entities.findById(id).isPresent()) {
                return id;
            }
            return entities
                    .findByExternalRef(trimmed)
                    .map(SystemEntity::getId)
                    .orElseThrow(() -> new EntityNotFoundException("SystemEntity not found: " + raw));
        }
        return entities
                .findByExternalRef(trimmed)
                .map(SystemEntity::getId)
                .orElseThrow(() -> new EntityNotFoundException("SystemEntity not found: " + raw));
    }

    /** Impact-query identifier semantics over one captured snapshot. */
    public static SimulationProposalValidator.EntityResolver snapshotResolver(
            Map<UUID, SystemEntity> entitiesById) {
        return raw -> {
            String trimmed = raw.trim();
            UUID id = tryParseUuid(trimmed);
            if (id != null && entitiesById.containsKey(id)) {
                return id;
            }
            for (SystemEntity entity : entitiesById.values()) {
                if (trimmed.equals(entity.getExternalRef())) {
                    return entity.getId();
                }
            }
            throw new EntityNotFoundException("SystemEntity not found: " + raw);
        };
    }

    private static UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
