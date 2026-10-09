package com.soubhagya.systivex.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.twin.AbstractPostgresIntegrationTest;
import com.soubhagya.systivex.twin.api.CreateSystemEntityRequest;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import com.soubhagya.systivex.twin.service.TwinService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sync behaviour against throwaway PostgreSQL: full graph creation,
 * idempotent re-sync, manual-row preservation, ownership conflicts with
 * full rollback, and fail-closed invalid roots.
 */
@SpringBootTest
class TwinSyncServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired private TwinSyncService syncService;
    @Autowired private TwinService twinService;
    @Autowired private SystemEntityRepository entities;
    @Autowired private SystemRelationshipRepository relationships;

    private Path realRoot;

    @BeforeEach
    void resolveRealRoot() {
        realRoot = Path.of("").toAbsolutePath().resolve("target-services");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isDirectory(realRoot), "real target-services tree not present");
    }

    @BeforeEach
    void cleanDatabase() {
        relationships.deleteAll();
        entities.deleteAll();
    }

    @Test
    void emptyTwinSyncProducesExpectedGraph() {
        SyncResult result = syncService.synchronize(realRoot);

        assertThat(result.entitiesCreated()).isEqualTo(15);
        assertThat(result.entitiesUpdated()).isZero();
        assertThat(result.entitiesUnchanged()).isZero();
        assertThat(result.relationshipsCreated()).isEqualTo(18);
        assertThat(result.relationshipsUnchanged()).isZero();
        assertThat(entities.count()).isEqualTo(15);
        assertThat(relationships.count()).isEqualTo(18);
    }

    @Test
    void repeatedSyncIsIdempotent() {
        SyncResult first = syncService.synchronize(realRoot);
        SyncResult second = syncService.synchronize(realRoot);

        assertThat(first.entitiesCreated()).isEqualTo(15);
        assertThat(second.entitiesCreated()).isZero();
        assertThat(second.entitiesUpdated()).isZero();
        assertThat(second.entitiesUnchanged()).isEqualTo(15);
        assertThat(second.relationshipsCreated()).isZero();
        assertThat(second.relationshipsUnchanged()).isEqualTo(18);
        assertThat(entities.count()).isEqualTo(15);
        assertThat(relationships.count()).isEqualTo(18);
    }

    @Test
    void manualRowsSurviveSyncUnchanged() {
        twinService.createEntity(
                new CreateSystemEntityRequest(
                        SystemEntityType.SERVICE,
                        "hand-drawn",
                        "manual:hand-drawn",
                        "dev",
                        Map.of("owner", "operator")));

        SyncResult result = syncService.synchronize(realRoot);

        assertThat(result.entitiesCreated()).isEqualTo(15);
        assertThat(entities.count()).isEqualTo(16);
        assertThat(entities.findByExternalRef("manual:hand-drawn")).isPresent();
        assertThat(
                        entities.findByExternalRef("manual:hand-drawn").orElseThrow().getMetadata())
                .containsEntry("owner", "operator");
    }

    @Test
    void ownershipConflictFailsWithoutPartialWrites() {
        // A manual row squats on an identity the connector is about to claim.
        twinService.createEntity(
                new CreateSystemEntityRequest(
                        SystemEntityType.API, "impostor", "target-service:order-service", "dev", null));

        assertThatThrownBy(() -> syncService.synchronize(realRoot))
                .isInstanceOf(OwnershipConflictException.class)
                .hasMessageContaining("not connector-managed");

        // The transaction rolled back: no connector rows, only the squatter.
        assertThat(entities.count()).isEqualTo(1);
        assertThat(relationships.count()).isZero();
    }

    @Test
    void invalidRootFailsWithoutWrites() {
        assertThatThrownBy(
                        () ->
                                syncService.synchronize(
                                        realRoot.resolve("does-not-exist")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(entities.count()).isZero();
        assertThat(relationships.count()).isZero();
    }
}
