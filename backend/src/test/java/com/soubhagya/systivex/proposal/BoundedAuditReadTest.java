package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.proposal.model.AuditAction;
import com.soubhagya.systivex.proposal.model.AuditEvent;
import com.soubhagya.systivex.proposal.model.AuditOutcome;
import com.soubhagya.systivex.proposal.model.ProposalRevision;
import com.soubhagya.systivex.proposal.repository.AuditEventRepository;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.service.ProposalService;
import com.soubhagya.systivex.twin.repository.SystemEntityRepository;
import com.soubhagya.systivex.twin.repository.SystemRelationshipRepository;
import com.soubhagya.systivex.proposal.service.AuditService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BoundedAuditReadTest {

    @Test
    void boundedAuditTrailHandlesZeroEvents() {
        ProposalRevisionRepository revisions = Mockito.mock(ProposalRevisionRepository.class);
        AuditEventRepository events = Mockito.mock(AuditEventRepository.class);
        AuditService audits = Mockito.mock(AuditService.class);
        SystemEntityRepository entities = Mockito.mock(SystemEntityRepository.class);
        SystemRelationshipRepository relationships = Mockito.mock(SystemRelationshipRepository.class);
        ProposalService service = new ProposalService(revisions, events, audits, entities, relationships);

        UUID key = UUID.randomUUID();
        when(revisions.findFirstByProposalKeyOrderByRevisionNumberDesc(key))
                .thenReturn(Optional.of(Mockito.mock(ProposalRevision.class)));
        when(events.findTop101ByProposalKeyOrderBySeqAsc(key)).thenReturn(List.of());

        ProposalService.BoundedAuditTrail result = service.boundedAuditTrail(key);

        assertThat(result.events()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.limit()).isEqualTo(100);
    }

    @Test
    void boundedAuditTrailHandles100EventsWithoutTruncation() {
        ProposalRevisionRepository revisions = Mockito.mock(ProposalRevisionRepository.class);
        AuditEventRepository events = Mockito.mock(AuditEventRepository.class);
        AuditService audits = Mockito.mock(AuditService.class);
        SystemEntityRepository entities = Mockito.mock(SystemEntityRepository.class);
        SystemRelationshipRepository relationships = Mockito.mock(SystemRelationshipRepository.class);
        ProposalService service = new ProposalService(revisions, events, audits, entities, relationships);

        UUID key = UUID.randomUUID();
        when(revisions.findFirstByProposalKeyOrderByRevisionNumberDesc(key))
                .thenReturn(Optional.of(Mockito.mock(ProposalRevision.class)));
        List<AuditEvent> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AuditEvent event = Mockito.mock(AuditEvent.class);
            when(event.getSeq()).thenReturn((long) (i + 1));
            when(event.getAction()).thenReturn(AuditAction.CREATED);
            when(event.getOutcome()).thenReturn(AuditOutcome.SUCCESS);
            when(event.getActorSubject()).thenReturn("subject");
            when(event.getProposalKey()).thenReturn(key);
            when(event.getRevisionNumber()).thenReturn(1);
            when(event.getPrevHash()).thenReturn("prev");
            when(event.getEventHash()).thenReturn("hash");
            rows.add(event);
        }
        when(events.findTop101ByProposalKeyOrderBySeqAsc(key)).thenReturn(rows);

        ProposalService.BoundedAuditTrail result = service.boundedAuditTrail(key);

        assertThat(result.events()).hasSize(100);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void boundedAuditTrailHandles101EventsWithTruncation() {
        ProposalRevisionRepository revisions = Mockito.mock(ProposalRevisionRepository.class);
        AuditEventRepository events = Mockito.mock(AuditEventRepository.class);
        AuditService audits = Mockito.mock(AuditService.class);
        SystemEntityRepository entities = Mockito.mock(SystemEntityRepository.class);
        SystemRelationshipRepository relationships = Mockito.mock(SystemRelationshipRepository.class);
        ProposalService service = new ProposalService(revisions, events, audits, entities, relationships);

        UUID key = UUID.randomUUID();
        when(revisions.findFirstByProposalKeyOrderByRevisionNumberDesc(key))
                .thenReturn(Optional.of(Mockito.mock(ProposalRevision.class)));
        List<AuditEvent> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            AuditEvent event = Mockito.mock(AuditEvent.class);
            when(event.getSeq()).thenReturn((long) (i + 1));
            when(event.getAction()).thenReturn(AuditAction.CREATED);
            when(event.getOutcome()).thenReturn(AuditOutcome.SUCCESS);
            when(event.getActorSubject()).thenReturn("subject");
            when(event.getProposalKey()).thenReturn(key);
            when(event.getRevisionNumber()).thenReturn(1);
            when(event.getPrevHash()).thenReturn("prev");
            when(event.getEventHash()).thenReturn("hash");
            rows.add(event);
        }
        when(events.findTop101ByProposalKeyOrderBySeqAsc(key)).thenReturn(rows);

        ProposalService.BoundedAuditTrail result = service.boundedAuditTrail(key);

        assertThat(result.events()).hasSize(100);
        assertThat(result.truncated()).isTrue();
        assertThat(result.events().get(0).seq()).isEqualTo(1L);
        assertThat(result.events().get(99).seq()).isEqualTo(100L);
    }
}
