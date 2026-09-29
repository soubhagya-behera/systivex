package com.soubhagya.systivex.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TwinRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired private SystemEntityRepository entities;

    @Autowired private SystemRelationshipRepository relationships;

    @Autowired private EntityManager entityManager;

    @Test
    void persistsEntityWithJsonbMetadata() {
        SystemEntity saved =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.SERVICE,
                                "billing-api",
                                "svc:billing-api",
                                "prod",
                                Map.of("owner", "payments", "tier", 1)));

        entityManager.clear();

        SystemEntity found = entities.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("billing-api");
        assertThat(found.getMetadata()).containsEntry("owner", "payments");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void duplicateExternalRefViolatesUniqueConstraint() {
        entities.saveAndFlush(
                new SystemEntity(
                        SystemEntityType.API, "orders", "api:orders", null, null));

        assertThatThrownBy(
                        () ->
                                entities.saveAndFlush(
                                        new SystemEntity(
                                                SystemEntityType.API,
                                                "orders-copy",
                                                "api:orders",
                                                null,
                                                null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void relationshipRequiresExistingEndpoints() {
        SystemEntity ghost = entityManager.getReference(SystemEntity.class, UUID.randomUUID());
        SystemEntity real =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.SERVICE, "real", null, null, null));

        SystemRelationship broken =
                new SystemRelationship(ghost, real, SystemRelationshipType.CALLS);
        assertThatThrownBy(() -> relationships.saveAndFlush(broken))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void selfReferenceViolatesCheckConstraint() {
        SystemEntity entity =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.DATABASE, "ledger", null, null, null));

        assertThatThrownBy(
                        () ->
                                relationships.saveAndFlush(
                                        new SystemRelationship(
                                                entity, entity, SystemRelationshipType.READS)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateRelationshipTripleViolatesUniqueConstraint() {
        SystemEntity source =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.SERVICE, "web", null, null, null));
        SystemEntity target =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.DATABASE, "db", null, null, null));

        relationships.saveAndFlush(
                new SystemRelationship(source, target, SystemRelationshipType.WRITES));

        assertThatThrownBy(
                        () ->
                                relationships.saveAndFlush(
                                        new SystemRelationship(
                                                source, target, SystemRelationshipType.WRITES)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingEntityCascadesItsRelationships() {
        SystemEntity source =
                entities.saveAndFlush(
                        new SystemEntity(
                                SystemEntityType.SERVICE, "edge", null, null, null));
        SystemEntity target =
                entities.saveAndFlush(
                        new SystemEntity(SystemEntityType.API, "inner", null, null, null));
        SystemRelationship rel =
                relationships.saveAndFlush(
                        new SystemRelationship(source, target, SystemRelationshipType.CALLS));
        UUID sourceId = source.getId();
        UUID relId = rel.getId();

        // Detach so the delete goes straight to the database and exercises
        // the ON DELETE CASCADE foreign keys (Hibernate has no cascade here).
        entityManager.flush();
        entityManager.clear();

        entities.delete(entities.getReferenceById(sourceId));
        entities.flush();

        assertThat(relationships.existsById(relId)).isFalse();
        assertThat(entities.existsById(sourceId)).isFalse();
    }
}
