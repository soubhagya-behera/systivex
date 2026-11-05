package com.soubhagya.systivex.twin.service;

import com.soubhagya.systivex.twin.api.CreateSystemEntityRequest;
import com.soubhagya.systivex.twin.api.CreateSystemRelationshipRequest;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1 twin operations: plain CRUD over the persisted entity/relationship
 * graph. No ingestion, inference, or traversal logic lives here yet.
 */
@Service
@Transactional
public class TwinService {

    private final SystemEntityRepository entities;
    private final SystemRelationshipRepository relationships;

    public TwinService(
            SystemEntityRepository entities, SystemRelationshipRepository relationships) {
        this.entities = entities;
        this.relationships = relationships;
    }

    public SystemEntity createEntity(CreateSystemEntityRequest request) {
        SystemEntity entity =
                new SystemEntity(
                        request.type(),
                        request.name(),
                        request.externalRef(),
                        request.environment(),
                        request.metadata());
        return entities.save(entity);
    }

    @Transactional(readOnly = true)
    public SystemEntity getEntity(UUID id) {
        return entities
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SystemEntity not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SystemEntity> listEntities() {
        return entities.findAll();
    }

    public SystemRelationship createRelationship(CreateSystemRelationshipRequest request) {
        if (request.sourceId().equals(request.targetId())) {
            throw new IllegalArgumentException(
                    "A relationship cannot reference the same entity twice: "
                            + request.sourceId());
        }
        SystemEntity source = getEntity(request.sourceId());
        SystemEntity target = getEntity(request.targetId());
        return relationships.save(new SystemRelationship(source, target, request.type()));
    }

    @Transactional(readOnly = true)
    public SystemRelationship getRelationship(UUID id) {
        return relationships
                .findById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException("SystemRelationship not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SystemRelationship> listRelationships() {
        return relationships.findAll();
    }

    static final int MAX_GRAPH_ENTITIES = 500;
    static final int MAX_GRAPH_RELATIONSHIPS = 2000;
    static final int ENTITY_OVERFLOW_PROBE = 501;
    static final int RELATIONSHIP_OVERFLOW_PROBE = 2001;

    /**
     * Reads a complete, bounded twin graph snapshot for deterministic analysis.
     *
     * <p>The snapshot is read-only and uses deterministic ordering. One extra
     * record is fetched for each collection only to detect overflow. If either
     * collection exceeds its bound, the snapshot is marked truncated and is not
     * available for analysis. No partial graph is ever returned as a complete
     * analysis input.
     */
    @Transactional(readOnly = true)
    public TwinGraphSnapshot readGraphSnapshot() {
        Page<SystemEntity> entityPage =
                entities.findAll(
                        PageRequest.of(
                                0,
                                ENTITY_OVERFLOW_PROBE,
                                Sort.by(
                                        Sort.Order.asc("externalRef").nullsLast(),
                                        Sort.Order.asc("id"))));
        Page<SystemRelationship> relationshipPage =
                relationships.findAll(
                        PageRequest.of(
                                0,
                                RELATIONSHIP_OVERFLOW_PROBE,
                                Sort.by(
                                        Sort.Order.asc("source.id"),
                                        Sort.Order.asc("target.id"),
                                        Sort.Order.asc("relationshipType"),
                                        Sort.Order.asc("id"))));

        List<SystemEntity> entityRows = entityPage.getContent();
        List<SystemRelationship> relationshipRows = relationshipPage.getContent();

        boolean entityOverflow = entityRows.size() >= ENTITY_OVERFLOW_PROBE;
        boolean relationshipOverflow = relationshipRows.size() >= RELATIONSHIP_OVERFLOW_PROBE;

        List<SystemEntity> acceptedEntities =
                entityOverflow
                        ? new ArrayList<>(entityRows.subList(0, MAX_GRAPH_ENTITIES))
                        : new ArrayList<>(entityRows);
        List<SystemRelationship> acceptedRelationships =
                relationshipOverflow
                        ? new ArrayList<>(relationshipRows.subList(0, MAX_GRAPH_RELATIONSHIPS))
                        : new ArrayList<>(relationshipRows);

        Set<UUID> entityIds = new HashSet<>();
        for (SystemEntity entity : acceptedEntities) {
            entityIds.add(entity.getId());
        }
        boolean consistent = true;
        for (SystemRelationship relationship : acceptedRelationships) {
            SystemEntity source = relationship.getSource();
            SystemEntity target = relationship.getTarget();
            UUID sourceId = source == null ? null : source.getId();
            UUID targetId = target == null ? null : target.getId();
            boolean sourcePresent = sourceId == null
                    ? acceptedEntities.contains(source)
                    : entityIds.contains(sourceId);
            boolean targetPresent = targetId == null
                    ? acceptedEntities.contains(target)
                    : entityIds.contains(targetId);
            if (!sourcePresent || !targetPresent) {
                consistent = false;
                break;
            }
        }

        boolean truncated = entityOverflow || relationshipOverflow;
        boolean availableForAnalysis = !truncated && consistent;

        List<String> limitations = new ArrayList<>();
        if (entityOverflow) {
            limitations.add(
                    "entity count exceeded " + MAX_GRAPH_ENTITIES
                            + "; graph snapshot truncated and analysis refused");
        }
        if (relationshipOverflow) {
            limitations.add(
                    "relationship count exceeded " + MAX_GRAPH_RELATIONSHIPS
                            + "; graph snapshot truncated and analysis refused");
        }
        if (!consistent) {
            limitations.add(
                    "relationship endpoint missing from accepted entity snapshot; graph analysis refused");
        }

        int entityCount =
                entityOverflow ? ENTITY_OVERFLOW_PROBE : acceptedEntities.size();
        int relationshipCount =
                relationshipOverflow
                        ? RELATIONSHIP_OVERFLOW_PROBE
                        : acceptedRelationships.size();

        return new TwinGraphSnapshot(
                acceptedEntities,
                acceptedRelationships,
                entityCount,
                relationshipCount,
                truncated,
                availableForAnalysis,
                consistent,
                limitations);
    }
}
