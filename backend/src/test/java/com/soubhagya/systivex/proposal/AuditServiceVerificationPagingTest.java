package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.proposal.model.AuditAction;
import com.soubhagya.systivex.proposal.model.AuditChainHead;
import com.soubhagya.systivex.proposal.model.AuditEvent;
import com.soubhagya.systivex.proposal.model.AuditOutcome;
import com.soubhagya.systivex.proposal.repository.AuditChainHeadRepository;
import com.soubhagya.systivex.proposal.repository.AuditEventRepository;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.service.AuditHash;
import com.soubhagya.systivex.proposal.service.AuditService;
import com.soubhagya.systivex.proposal.service.AuditVerification;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/**
 * The audit chain is read in pages, never as one materialised table.
 *
 * <p>These tests pin the repository access pattern of the verification: it
 * pages the chain with a moving cursor over the sequence index, releases
 * each page from the persistence context, and never reads the whole event
 * table.
 */
class AuditServiceVerificationPagingTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-02T03:04:05Z");

    private static final UUID PROPOSAL_KEY = UUID.randomUUID();

    private static final String OPS_HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    /** A valid chain of {@code count} events, linked exactly as the appender links them. */
    private static List<AuditEvent> chain(int count) {
        List<AuditEvent> events = new ArrayList<>(count);
        String previousHash = AuditChainHead.GENESIS_HASH;
        for (int seq = 1; seq <= count; seq++) {
            String correlationId = "corr-" + seq;
            String eventHash =
                    AuditHash.hash(
                            seq,
                            OCCURRED_AT,
                            "https://issuer.example",
                            "actor-subject",
                            AuditAction.CREATED.name(),
                            AuditOutcome.SUCCESS.name(),
                            PROPOSAL_KEY,
                            1,
                            OPS_HASH,
                            null,
                            "recorded for audit",
                            correlationId,
                            previousHash);
            AuditEvent event =
                    new AuditEvent(
                            seq,
                            "https://issuer.example",
                            "actor-subject",
                            AuditAction.CREATED,
                            AuditOutcome.SUCCESS,
                            PROPOSAL_KEY,
                            1,
                            previousHash,
                            eventHash);
            event.setOccurredAt(OCCURRED_AT);
            event.setOperationsHash(OPS_HASH);
            event.setAssessmentHash(null);
            event.setReason("recorded for audit");
            event.setCorrelationId(correlationId);
            events.add(event);
            previousHash = eventHash;
        }
        return events;
    }

    @Test
    void verificationPagesTheChainAndNeverReadsTheWholeEventTable() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        AuditChainHeadRepository head = mock(AuditChainHeadRepository.class);
        ProposalRevisionRepository revisions = mock(ProposalRevisionRepository.class);
        EntityManager entityManager = mock(EntityManager.class);

        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        List<AuditEvent> chain = chain(count);
        List<Long> cursors = new ArrayList<>();

        when(head.findById(AuditChainHead.HEAD_ID))
                .thenReturn(
                        Optional.of(
                                new AuditChainHead(
                                        count, chain.get(count - 1).getEventHash())));
        when(events.findBySeqGreaterThanOrderBySeqAsc(anyLong(), any(Pageable.class)))
                .thenAnswer(
                        invocation -> {
                            long after = invocation.getArgument(0);
                            cursors.add(after);
                            List<AuditEvent> page = new ArrayList<>();
                            for (AuditEvent event : chain) {
                                if (event.getSeq() > after) {
                                    page.add(event);
                                }
                            }
                            return page.subList(
                                    0, Math.min(page.size(), AuditService.VERIFICATION_PAGE_SIZE));
                        });

        AuditService service = new AuditService(events, head, revisions, entityManager);
        AuditVerification result = service.verify();

        assertThat(result.status()).isEqualTo(AuditVerification.OK);
        assertThat(result.eventCount()).isEqualTo(count);
        assertThat(result.headSeq()).isEqualTo(count);
        assertThat(result.brokenAtSeq()).isNull();

        // The chain is walked with a moving cursor, one page at a time, and
        // the whole event table is never read.
        assertThat(cursors).containsExactly(0L, (long) AuditService.VERIFICATION_PAGE_SIZE);
        verify(events, never()).findAllByOrderBySeqAsc();
        verify(entityManager, atLeastOnce()).clear();
    }
}
