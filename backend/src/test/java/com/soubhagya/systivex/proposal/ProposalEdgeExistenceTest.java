package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.intelligence.SimulationOperation;
import com.soubhagya.systivex.intelligence.SimulationOperationType;
import com.soubhagya.systivex.intelligence.SimulationProposalValidator;
import com.soubhagya.systivex.proposal.api.CreateProposalRequest;
import com.soubhagya.systivex.proposal.repository.AuditEventRepository;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.service.AuditService;
import com.soubhagya.systivex.proposal.service.ProposalOperations;
import com.soubhagya.systivex.proposal.service.ProposalService;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The proposal conflict check asks only about the edges a proposal proposes.
 *
 * <p>It used to read the whole relationship table to answer those questions.
 * These tests pin the bounded behaviour: an indexed existence lookup per
 * proposed edge, and never a full materialisation of the table.
 */
class ProposalEdgeExistenceTest {

    private static final UUID SVC_A = UUID.randomUUID();
    private static final UUID SVC_B = UUID.randomUUID();

    @Test
    void storedEdgeExistenceUsesTheIndexedLookupAndNeverReadsTheWholeTable() {
        SystemRelationshipRepository relationships = mock(SystemRelationshipRepository.class);
        when(relationships.existsBySourceIdAndTargetIdAndRelationshipType(
                        SVC_A, SVC_B, SystemRelationshipType.CALLS))
                .thenReturn(true);

        SimulationProposalValidator.EdgeExistence oracle =
                ProposalOperations.storedEdgeExistence(relationships);

        assertThat(oracle.exists(SVC_A, SVC_B, SystemRelationshipType.CALLS)).isTrue();
        assertThat(oracle.exists(SVC_A, SVC_B, SystemRelationshipType.DEPENDS_ON)).isFalse();
        assertThat(oracle.exists(SVC_B, SVC_A, SystemRelationshipType.CALLS)).isFalse();
        verify(relationships, times(3))
                .existsBySourceIdAndTargetIdAndRelationshipType(any(), any(), any());
        verify(relationships, never()).findAll();
    }

    @Test
    void creatingADraftNeverReadsTheWholeRelationshipTable() {
        SystemEntityRepository entities = mock(SystemEntityRepository.class);
        SystemRelationshipRepository relationships = mock(SystemRelationshipRepository.class);
        ProposalRevisionRepository revisions = mock(ProposalRevisionRepository.class);
        AuditEventRepository events = mock(AuditEventRepository.class);
        AuditService audits = mock(AuditService.class);

        when(entities.findByExternalRef("svc-a")).thenReturn(Optional.of(entity(SVC_A, "svc-a")));
        when(entities.findByExternalRef("svc-b")).thenReturn(Optional.of(entity(SVC_B, "svc-b")));
        // The proposed edge does not exist yet, so the draft is valid.
        when(relationships.existsBySourceIdAndTargetIdAndRelationshipType(
                        SVC_A, SVC_B, SystemRelationshipType.CALLS))
                .thenReturn(false);
        when(revisions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProposalService service =
                new ProposalService(revisions, events, audits, entities, relationships);
        service.create(
                "issuer",
                "subject",
                new CreateProposalRequest(
                        "Change",
                        null,
                        List.of(
                                new SimulationOperation(
                                        SimulationOperationType.ADD_RELATIONSHIP,
                                        null,
                                        "svc-a",
                                        "svc-b",
                                        SystemRelationshipType.CALLS))));

        // Exactly one indexed lookup for the one proposed edge, and no read of
        // the relationship table.
        verify(relationships, times(1))
                .existsBySourceIdAndTargetIdAndRelationshipType(
                        SVC_A, SVC_B, SystemRelationshipType.CALLS);
        verify(relationships, never()).findAll();
    }

    private static SystemEntity entity(UUID id, String externalRef) {
        SystemEntity entity =
                new SystemEntity(
                        SystemEntityType.SERVICE, externalRef, externalRef, "test", Map.of());
        try {
            Field field = SystemEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }
}
