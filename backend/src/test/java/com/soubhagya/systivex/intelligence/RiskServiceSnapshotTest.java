package com.soubhagya.systivex.intelligence;

import static com.soubhagya.systivex.intelligence.ImpactTestFixtures.edge;
import static com.soubhagya.systivex.intelligence.ImpactTestFixtures.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The risk assessment classifies the entity snapshot the impact traversal
 * already read. It must not read the entity table a second time: the twin
 * graph is unbounded, so every extra full read is an extra full materialisation
 * of the whole entity table (including its jsonb metadata) per request.
 */
class RiskServiceSnapshotTest {

    @Test
    void riskAssessmentReadsTheEntityTableOnce() {
        SystemEntityRepository entities = mock(SystemEntityRepository.class);
        SystemRelationshipRepository relationships = mock(SystemRelationshipRepository.class);
        SystemEntity start = entity("svc:a", SystemEntityType.SERVICE);
        SystemEntity neighbour = entity("db:b", SystemEntityType.DATABASE);
        when(entities.findAll()).thenReturn(List.of(start, neighbour));
        when(relationships.findAll())
                .thenReturn(
                        List.of(edge(start, neighbour, SystemRelationshipType.CALLS)));
        when(entities.findByExternalRef("svc:a")).thenReturn(Optional.of(start));

        ImpactAnalysisService impactService = new ImpactAnalysisService(entities, relationships);
        RiskService riskService = new RiskService(impactService);

        RiskResponse response = riskService.assess(List.of("svc:a"), null, null);

        // The classification itself is unchanged: the neighbour is one hop
        // away, so it is structurally medium risk and carries no runtime
        // uncertainty.
        RiskResponse.RiskEntry entry =
                response.risks().stream()
                        .filter(e -> "db:b".equals(e.externalRef()))
                        .findFirst()
                        .orElseThrow();
        assertThat(entry.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(entry.depth()).isEqualTo(1);
        assertThat(entry.uncertain()).isFalse();
        assertThat(entry.stale()).isFalse();

        // One entity read and one relationship read per assessment: the
        // classification reuses the snapshot the traversal already read.
        verify(entities, times(1)).findAll();
        verify(relationships, times(1)).findAll();
    }
}
