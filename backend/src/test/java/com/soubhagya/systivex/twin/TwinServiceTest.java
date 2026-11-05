package com.soubhagya.systivex.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.twin.api.CreateSystemEntityRequest;
import com.soubhagya.systivex.twin.api.CreateSystemRelationshipRequest;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import com.soubhagya.systivex.twin.service.TwinGraphSnapshot;
import com.soubhagya.systivex.twin.service.TwinService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TwinServiceTest {

    @Mock private SystemEntityRepository entities;

    @Mock private SystemRelationshipRepository relationships;

    @InjectMocks private TwinService service;

    private SystemEntity entity(SystemEntityType type, String name) {
        return new SystemEntity(type, name, null, null, null);
    }

    @Test
    void createEntityPersistsAndReturnsIt() {
        SystemEntity saved = entity(SystemEntityType.SERVICE, "checkout");
        when(entities.save(any(SystemEntity.class))).thenReturn(saved);

        SystemEntity result =
                service.createEntity(
                        new CreateSystemEntityRequest(
                                SystemEntityType.SERVICE, "checkout", null, "prod", null));

        assertThat(result).isSameAs(saved);
        verify(entities).save(any(SystemEntity.class));
    }

    @Test
    void createEntityDefaultsNullMetadataToEmpty() {
        when(entities.save(any(SystemEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SystemEntity result =
                service.createEntity(
                        new CreateSystemEntityRequest(
                                SystemEntityType.API, "pricing", null, null, null));

        assertThat(result.getMetadata()).isEmpty();
    }

    @Test
    void getEntityThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(entities.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEntity(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listEntitiesReturnsAll() {
        List<SystemEntity> all =
                List.of(
                        entity(SystemEntityType.SERVICE, "a"),
                        entity(SystemEntityType.DATABASE, "b"));
        when(entities.findAll()).thenReturn(all);

        assertThat(service.listEntities()).hasSize(2);
    }

    @Test
    void createRelationshipLoadsBothEndpoints() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SystemEntity source = entity(SystemEntityType.SERVICE, "web");
        SystemEntity target = entity(SystemEntityType.DATABASE, "db");
        when(entities.findById(sourceId)).thenReturn(Optional.of(source));
        when(entities.findById(targetId)).thenReturn(Optional.of(target));
        when(relationships.save(any(SystemRelationship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SystemRelationship result =
                service.createRelationship(
                        new CreateSystemRelationshipRequest(
                                sourceId, targetId, SystemRelationshipType.READS));

        assertThat(result.getSource()).isSameAs(source);
        assertThat(result.getTarget()).isSameAs(target);
    }

    @Test
    void createRelationshipRejectsSelfReferenceWithoutTouchingTheDatabase() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.createRelationship(
                                        new CreateSystemRelationshipRequest(
                                                id, id, SystemRelationshipType.CALLS)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(entities, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void createRelationshipThrowsWhenSourceMissing() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(entities.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.createRelationship(
                                        new CreateSystemRelationshipRequest(
                                                sourceId,
                                                targetId,
                                                SystemRelationshipType.DEPENDS_ON)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getRelationshipThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(relationships.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRelationship(id))
                .isInstanceOf(EntityNotFoundException.class);
    }
    private Page<SystemEntity> entityPage(List<SystemEntity> rows) {
        return new PageImpl<>(rows);
    }

    private Page<SystemRelationship> relationshipPage(List<SystemRelationship> rows) {
        return new PageImpl<>(rows);
    }

    @Test
    void readGraphSnapshotEmptyGraphIsAvailable() {
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(List.of()));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(List.of()));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.entities()).isEmpty();
        assertThat(snapshot.relationships()).isEmpty();
        assertThat(snapshot.entityCount()).isZero();
        assertThat(snapshot.relationshipCount()).isZero();
        assertThat(snapshot.truncated()).isFalse();
        assertThat(snapshot.availableForAnalysis()).isTrue();
        assertThat(snapshot.consistent()).isTrue();
    }

    @Test
    void readGraphSnapshotExactly500EntitiesIsAvailable() {
        List<SystemEntity> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(entity(SystemEntityType.SERVICE, "s" + i));
        }
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(rows));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(List.of()));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.entities()).hasSize(500);
        assertThat(snapshot.entityCount()).isEqualTo(500);
        assertThat(snapshot.truncated()).isFalse();
        assertThat(snapshot.availableForAnalysis()).isTrue();
    }

    @Test
    void readGraphSnapshot501EntitiesIsTruncatedAndUnavailable() {
        List<SystemEntity> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(entity(SystemEntityType.SERVICE, "s" + i));
        }
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(rows));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(List.of()));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.entities()).hasSize(500);
        assertThat(snapshot.entityCount()).isEqualTo(501);
        assertThat(snapshot.truncated()).isTrue();
        assertThat(snapshot.availableForAnalysis()).isFalse();
    }

    @Test
    void readGraphSnapshotExactly2000RelationshipsIsAvailable() {
        SystemEntity source = entity(SystemEntityType.SERVICE, "source");
        SystemEntity target = entity(SystemEntityType.DATABASE, "target");
        List<SystemRelationship> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            rows.add(new SystemRelationship(source, target, SystemRelationshipType.READS));
        }
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(List.of(source, target)));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(rows));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.relationships()).hasSize(2000);
        assertThat(snapshot.relationshipCount()).isEqualTo(2000);
        assertThat(snapshot.truncated()).isFalse();
        assertThat(snapshot.availableForAnalysis()).isTrue();
    }

    @Test
    void readGraphSnapshot2001RelationshipsIsTruncatedAndUnavailable() {
        SystemEntity source = entity(SystemEntityType.SERVICE, "source");
        SystemEntity target = entity(SystemEntityType.DATABASE, "target");
        List<SystemRelationship> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            rows.add(new SystemRelationship(source, target, SystemRelationshipType.READS));
        }
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(List.of(source, target)));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(rows));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.relationships()).hasSize(2000);
        assertThat(snapshot.relationshipCount()).isEqualTo(2001);
        assertThat(snapshot.truncated()).isTrue();
        assertThat(snapshot.availableForAnalysis()).isFalse();
    }

    @Test
    void readGraphSnapshotMissingRelationshipEndpointIsUnavailable() {
        SystemEntity source = entity(SystemEntityType.SERVICE, "source");
        SystemEntity target = entity(SystemEntityType.DATABASE, "target");
        SystemEntity missing = entity(SystemEntityType.DATABASE, "missing");
        SystemRelationship relationship = new SystemRelationship(source, missing, SystemRelationshipType.READS);
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(List.of(source, target)));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(List.of(relationship)));

        TwinGraphSnapshot snapshot = service.readGraphSnapshot();

        assertThat(snapshot.availableForAnalysis()).isFalse();
        assertThat(snapshot.consistent()).isFalse();
        assertThat(snapshot.truncated()).isFalse();
    }

    @Test
    void readGraphSnapshotDoesNotMutateRepositories() {
        when(entities.findAll(any(Pageable.class))).thenReturn(entityPage(List.of()));
        when(relationships.findAll(any(Pageable.class))).thenReturn(relationshipPage(List.of()));

        service.readGraphSnapshot();

        verify(entities, org.mockito.Mockito.never()).save(any());
        verify(relationships, org.mockito.Mockito.never()).save(any());
    }

}
