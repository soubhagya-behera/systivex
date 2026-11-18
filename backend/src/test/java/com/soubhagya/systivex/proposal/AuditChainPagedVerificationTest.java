package com.soubhagya.systivex.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.proposal.model.AuditAction;
import com.soubhagya.systivex.proposal.model.AuditChainHead;
import com.soubhagya.systivex.proposal.model.AuditOutcome;
import com.soubhagya.systivex.proposal.model.ProposalRevision;
import com.soubhagya.systivex.proposal.model.ProposalStatus;
import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.service.AuditHash;
import com.soubhagya.systivex.proposal.service.AuditService;
import com.soubhagya.systivex.proposal.service.AuditVerification;
import com.soubhagya.systivex.twin.AbstractPostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verification of a chain longer than one page.
 *
 * <p>The chain is read in pages, so the whole history must still be checked:
 * a bounded response must never mean a bounded check. These tests build a
 * chain that spans several pages and prove that every event is verified,
 * that a tamper anywhere in the chain is still found and reported at the
 * right sequence number, and that a concurrent append never makes the
 * verification report a break that did not happen.
 */
@SpringBootTest
class AuditChainPagedVerificationTest extends AbstractPostgresIntegrationTest {

    private static final String OPS_HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Autowired private AuditService audits;

    @Autowired private ProposalRevisionRepository revisions;

    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void resetChain() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM proposal_revision");
        jdbc.update("UPDATE audit_chain_head SET last_seq = 0, last_hash = 'GENESIS'");
    }

    private UUID revisionFixture() {
        UUID key = UUID.randomUUID();
        revisions.saveAndFlush(
                new ProposalRevision(
                        key,
                        1,
                        ProposalStatus.DRAFT,
                        "Long chain",
                        "[{\"type\":\"REMOVE_ENTITY\",\"entity\":\"e1\"}]",
                        OPS_HASH,
                        "https://issuer.example",
                        "proposer-subject"));
        return key;
    }

    /** Writes a valid chain of {@code count} events and points the head at its end. */
    private String seedChain(UUID key, int count) {
        String previousHash = AuditChainHead.GENESIS_HASH;
        List<Object[]> rows = new ArrayList<>(count);
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
                            key,
                            1,
                            OPS_HASH,
                            null,
                            "recorded for audit",
                            correlationId,
                            previousHash);
            rows.add(
                    new Object[] {
                        UUID.randomUUID(),
                        seq,
                        Timestamp.from(OCCURRED_AT),
                        "https://issuer.example",
                        "actor-subject",
                        AuditAction.CREATED.name(),
                        AuditOutcome.SUCCESS.name(),
                        key,
                        1,
                        OPS_HASH,
                        null,
                        "recorded for audit",
                        correlationId,
                        previousHash,
                        eventHash
                    });
            previousHash = eventHash;
        }
        jdbc.batchUpdate(
                "INSERT INTO audit_event (id, seq, occurred_at, actor_issuer, actor_subject,"
                        + " action, outcome, proposal_key, revision_number, operations_hash,"
                        + " assessment_hash, reason, correlation_id, prev_hash, event_hash)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rows);
        jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ?", (long) count,
                previousHash);
        return previousHash;
    }

    @Test
    void aChainLongerThanOnePageIsVerifiedInFull() {
        UUID key = revisionFixture();
        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        seedChain(key, count);

        AuditVerification result = audits.verify();

        assertThat(result.status()).isEqualTo(AuditVerification.OK);
        assertThat(result.eventCount()).isEqualTo(count);
        assertThat(result.headSeq()).isEqualTo(count);
        assertThat(result.brokenAtSeq()).isNull();
        assertThat(result.detail()).isEqualTo("chain valid");
    }

    @Test
    void tamperBeyondTheFirstPageIsDetected() {
        UUID key = revisionFixture();
        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        seedChain(key, count);
        jdbc.update("UPDATE audit_event SET reason = 'forged' WHERE seq = ?", (long) count);

        AuditVerification result = audits.verify();

        assertThat(result.status()).isEqualTo(AuditVerification.BROKEN);
        assertThat(result.brokenAtSeq()).isEqualTo((long) count);
        assertThat(result.eventCount()).isEqualTo(count);
    }

    @Test
    void prevHashAcrossThePageBoundaryIsVerified() {
        UUID key = revisionFixture();
        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        seedChain(key, count);
        long firstOfSecondPage = AuditService.VERIFICATION_PAGE_SIZE + 1L;
        jdbc.update("UPDATE audit_event SET prev_hash = ? WHERE seq = ?", OPS_HASH,
                firstOfSecondPage);

        AuditVerification result = audits.verify();

        // The link across the page boundary is still checked, so a forged
        // prev_hash in the second page is found.
        assertThat(result.status()).isEqualTo(AuditVerification.BROKEN);
        assertThat(result.brokenAtSeq()).isEqualTo(firstOfSecondPage);
    }

    @Test
    void aMissingEventBeyondTheFirstPageIsDetected() {
        UUID key = revisionFixture();
        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        seedChain(key, count);
        long removed = AuditService.VERIFICATION_PAGE_SIZE / 2L;
        jdbc.update("DELETE FROM audit_event WHERE seq = ?", removed);

        AuditVerification result = audits.verify();

        assertThat(result.status()).isEqualTo(AuditVerification.BROKEN);
        // The gap shows up at the event that follows the removed one.
        assertThat(result.brokenAtSeq()).isEqualTo(removed + 1);
    }

    @Test
    void headMismatchOnALongChainIsDetected() {
        UUID key = revisionFixture();
        int count = AuditService.VERIFICATION_PAGE_SIZE + 1;
        seedChain(key, count);
        jdbc.update("UPDATE audit_chain_head SET last_hash = ?", OPS_HASH);

        AuditVerification result = audits.verify();

        assertThat(result.status()).isEqualTo(AuditVerification.BROKEN);
        assertThat(result.brokenAtSeq()).isEqualTo((long) count);
        assertThat(result.eventCount()).isEqualTo(count);
    }

    @Test
    void verificationDuringConcurrentAppendsNeverReportsASpuriousBreak() throws Exception {
        UUID key = revisionFixture();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> writers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final String correlation = "concurrent-" + i;
                writers.add(
                        pool.submit(
                                () -> {
                                    start.await(30, TimeUnit.SECONDS);
                                    for (int n = 0; n < 5; n++) {
                                        audits.append(
                                                new AuditService.AuditAppend(
                                                        "https://issuer.example",
                                                        "actor-subject",
                                                        AuditAction.CREATED,
                                                        AuditOutcome.SUCCESS,
                                                        key,
                                                        1,
                                                        OPS_HASH,
                                                        null,
                                                        "recorded for audit",
                                                        correlation + "-" + n));
                                    }
                                    return null;
                                }));
            }
            start.countDown();
            for (int i = 0; i < 20; i++) {
                // An append commits between the head read and the event read
                // must not make the verification report a break that did not
                // happen: the verification reads one consistent snapshot.
                assertThat(audits.verify().status()).isEqualTo(AuditVerification.OK);
            }
            for (Future<?> writer : writers) {
                writer.get(60, TimeUnit.SECONDS);
            }
            AuditVerification after = audits.verify();
            assertThat(after.status()).isEqualTo(AuditVerification.OK);
            assertThat(after.eventCount()).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }
}
