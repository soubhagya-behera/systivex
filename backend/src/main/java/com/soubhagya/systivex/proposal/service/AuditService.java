package com.soubhagya.systivex.proposal.service;

import com.soubhagya.systivex.proposal.model.AuditAction;
import com.soubhagya.systivex.proposal.model.AuditChainHead;
import com.soubhagya.systivex.proposal.model.AuditEvent;
import com.soubhagya.systivex.proposal.model.AuditOutcome;
import com.soubhagya.systivex.proposal.repository.AuditChainHeadRepository;
import com.soubhagya.systivex.proposal.repository.AuditEventRepository;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional append and read-only verification for the governance audit
 * chain (Phase 11 S2, D29). No proposal lifecycle logic lives here — only
 * the chain mechanics both later slices will share.
 *
 * <p>Append protocol, all in one transaction: lock the singleton head row,
 * allocate {@code last_seq + 1} (no database sequence, so rollbacks leave
 * no gaps), hash the canonical event fields with {@code prev_hash} set to
 * the head hash, insert the event, then advance the head. Any failure
 * rolls back both rows. Verification recomputes every link with the same
 * {@link AuditHash} implementation the appender uses.
 *
 * <p>Verification reads the chain in pages under a repeatable-read
 * snapshot, so the whole history is always checked — never a prefix — while
 * only one page is held in memory at a time.
 */
@Service
public class AuditService {

    /** Audit events read into memory at once while verifying the chain. */
    public static final int VERIFICATION_PAGE_SIZE = 500;

    private final AuditEventRepository events;

    private final AuditChainHeadRepository head;

    private final ProposalRevisionRepository revisions;

    private final EntityManager entityManager;

    public AuditService(
            AuditEventRepository events,
            AuditChainHeadRepository head,
            ProposalRevisionRepository revisions,
            EntityManager entityManager) {
        this.events = events;
        this.head = head;
        this.revisions = revisions;
        this.entityManager = entityManager;
    }

    /** Input for one chain append; every field lands in the hashed record. */
    public record AuditAppend(
            String actorIssuer,
            String actorSubject,
            AuditAction action,
            AuditOutcome outcome,
            UUID proposalKey,
            int revisionNumber,
            String operationsHash,
            String assessmentHash,
            String reason,
            String correlationId) {}

    @Transactional
    public AuditEvent append(AuditAppend append) {
        requireValid(append);
        if (revisions
                .findByProposalKeyAndRevisionNumber(append.proposalKey(), append.revisionNumber())
                .isEmpty()) {
            throw new EntityNotFoundException(
                    "Proposal revision not found: " + append.proposalKey() + " rev " + append.revisionNumber());
        }
        AuditChainHead chainHead =
                head.findHeadForUpdate().orElseThrow(() -> new IllegalStateException("Audit chain head is missing"));
        long seq = chainHead.getLastSeq() + 1;
        // Millis precision: PostgreSQL TIMESTAMPTZ keeps microseconds, so a
        // truncated-to-millis instant round-trips exactly and the hashed
        // timestamp always equals the stored one.
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String eventHash =
                AuditHash.hash(
                        seq,
                        occurredAt,
                        append.actorIssuer(),
                        append.actorSubject(),
                        append.action().name(),
                        append.outcome().name(),
                        append.proposalKey(),
                        append.revisionNumber(),
                        append.operationsHash(),
                        append.assessmentHash(),
                        append.reason(),
                        append.correlationId(),
                        chainHead.getLastHash());
        AuditEvent event =
                new AuditEvent(
                        seq,
                        append.actorIssuer(),
                        append.actorSubject(),
                        append.action(),
                        append.outcome(),
                        append.proposalKey(),
                        append.revisionNumber(),
                        chainHead.getLastHash(),
                        eventHash);
        event.setOccurredAt(occurredAt);
        event.setOperationsHash(append.operationsHash());
        event.setAssessmentHash(append.assessmentHash());
        event.setReason(append.reason());
        event.setCorrelationId(append.correlationId());
        AuditEvent stored = events.saveAndFlush(event);
        afterEventStored(stored);
        chainHead.setLastSeq(seq);
        chainHead.setLastHash(eventHash);
        head.saveAndFlush(chainHead);
        return stored;
    }

    /**
     * Test seam between the event insert and the head update. Production
     * code never overrides it; failure tests do, to prove both rows roll
     * back together.
     */
    protected void afterEventStored(AuditEvent stored) {}

    private static void requireValid(AuditAppend append) {
        if (append == null) {
            throw new IllegalArgumentException("Audit append input is required");
        }
        if (append.action() == null || append.outcome() == null) {
            throw new IllegalArgumentException("Audit action and outcome are required");
        }
        if (append.proposalKey() == null || append.revisionNumber() < 1) {
            throw new IllegalArgumentException("Audit proposal key and a positive revision are required");
        }
        if (append.actorSubject() == null || append.actorSubject().isBlank()) {
            throw new IllegalArgumentException("Audit actor subject is required");
        }
        if (append.actorIssuer() != null && append.actorIssuer().length() > 512) {
            throw new IllegalArgumentException("Audit actor issuer is too long");
        }
        if (append.reason() != null && append.reason().length() > 1024) {
            throw new IllegalArgumentException("Audit reason is too long");
        }
        if (append.correlationId() != null && append.correlationId().length() > 64) {
            throw new IllegalArgumentException("Audit correlation ID is too long");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditVerification verify() {
        return head.findById(AuditChainHead.HEAD_ID)
                .map(this::verifyAgainst)
                .orElseGet(() -> new AuditVerification(
                        AuditVerification.BROKEN, 0, -1, null, "chain head is missing"));
    }

    /**
     * Verifies the complete chain in pages, never materialising it.
     *
     * <p>The verification is a left fold: each event is checked against its
     * own stored fields, its sequence number, and the stored hash of the
     * event before it. Carrying that one hash forward is enough, so the
     * whole history is checked with bounded memory — the page size is the
     * only history held at any moment.
     *
     * <p>The result reports the total number of stored events, so the
     * bounded response shape is never confused with the amount of history
     * that was verified: the whole chain is always checked, and a break is
     * reported at the first sequence number where it is found.
     */
    private AuditVerification verifyAgainst(AuditChainHead chainHead) {
        long headSeq = chainHead.getLastSeq();
        String headHash = chainHead.getLastHash();

        long verified = 0;
        long expectedSeq = 1;
        String previousHash = AuditChainHead.GENESIS_HASH;
        String lastHash = null;
        long lastSeq = 0;
        Long brokenAt = null;
        String detail = null;

        long afterSeq = 0;
        boolean more = true;
        while (more && brokenAt == null) {
            List<AuditEvent> page =
                    events.findBySeqGreaterThanOrderBySeqAsc(
                            afterSeq, PageRequest.of(0, VERIFICATION_PAGE_SIZE));
            if (page.isEmpty()) {
                more = false;
                break;
            }
            for (AuditEvent event : page) {
                verified++;
                if (event.getSeq() != expectedSeq) {
                    brokenAt = event.getSeq();
                    detail = "sequence gap: expected seq " + expectedSeq;
                    break;
                }
                if (!previousHash.equals(event.getPrevHash())) {
                    brokenAt = event.getSeq();
                    detail = "prev_hash does not match preceding event hash";
                    break;
                }
                String recomputed =
                        AuditHash.hash(
                                event.getSeq(),
                                event.getOccurredAt(),
                                event.getActorIssuer(),
                                event.getActorSubject(),
                                event.getAction().name(),
                                event.getOutcome().name(),
                                event.getProposalKey(),
                                event.getRevisionNumber(),
                                event.getOperationsHash(),
                                event.getAssessmentHash(),
                                event.getReason(),
                                event.getCorrelationId(),
                                event.getPrevHash());
                if (!recomputed.equals(event.getEventHash())) {
                    brokenAt = event.getSeq();
                    detail = "event_hash does not match stored fields";
                    break;
                }
                previousHash = event.getEventHash();
                lastHash = event.getEventHash();
                lastSeq = event.getSeq();
                expectedSeq++;
                afterSeq = event.getSeq();
            }
            // Release this page's managed entities so the session never holds
            // the whole chain, however long the history is.
            entityManager.clear();
            more = page.size() == VERIFICATION_PAGE_SIZE;
        }

        if (brokenAt != null) {
            // The stored event count belongs in the result, so it is read
            // from the same snapshot instead of from the page that broke.
            return new AuditVerification(
                    AuditVerification.BROKEN, events.count(), headSeq, brokenAt, detail);
        }
        if (verified == 0) {
            if (headSeq == 0 && AuditChainHead.GENESIS_HASH.equals(headHash)) {
                return new AuditVerification(AuditVerification.OK, 0, 0, null, "empty chain at genesis");
            }
            return new AuditVerification(
                    AuditVerification.BROKEN, 0, headSeq, 1L, "no events but head has advanced");
        }
        if (headSeq != verified) {
            return new AuditVerification(
                    AuditVerification.BROKEN,
                    verified,
                    headSeq,
                    verified,
                    "head last_seq does not match stored event count");
        }
        if (!lastHash.equals(headHash)) {
            return new AuditVerification(
                    AuditVerification.BROKEN,
                    verified,
                    headSeq,
                    lastSeq,
                    "head last_hash does not match final event hash");
        }
        return new AuditVerification(AuditVerification.OK, verified, headSeq, null, "chain valid");
    }
}
