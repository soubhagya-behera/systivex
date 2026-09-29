package com.soubhagya.systivex.twin.service;

import com.soubhagya.systivex.twin.api.CreateSystemEntityRequest;
import com.soubhagya.systivex.twin.api.CreateSystemRelationshipRequest;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import java.util.List;
import java.util.UUID;
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
}
