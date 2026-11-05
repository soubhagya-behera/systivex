package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.proposal.model.ProposalRevision;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.service.ProposalLookup;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoundedReadFoundationsTest {

    @Mock
    private com.soubhagya.systivex.proposal.repository.AuditEventRepository events;

    @Mock
    private ProposalRevisionRepository revisions;

    @Test
    void latestLookupUsesBoundedQueryAndDoesNotLoadHistory() {
        UUID key = UUID.randomUUID();
        ProposalRevision revision = mock(ProposalRevision.class);
        when(revisions.findFirstByProposalKeyOrderByRevisionNumberDesc(key))
                .thenReturn(Optional.of(revision));

        assertThat(ProposalLookup.latestOrThrow(revisions, key)).isSameAs(revision);
        verify(revisions, never()).findByProposalKeyOrderByRevisionNumberAsc(key);
    }

    @Test
    void latestLookupMissingProposalThrowsNotFound() {
        UUID key = UUID.randomUUID();
        when(revisions.findFirstByProposalKeyOrderByRevisionNumberDesc(key))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ProposalLookup.latestOrThrow(revisions, key))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
