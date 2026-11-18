package com.soubhagya.systivex.proposal.repository;

import com.soubhagya.systivex.proposal.model.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    long countByProposalKey(UUID proposalKey);

    List<AuditEvent> findTop101ByProposalKeyOrderBySeqAsc(UUID proposalKey);

    List<AuditEvent> findAllByOrderBySeqAsc();

    /**
     * One page of the chain, ordered by sequence, starting strictly after
     * {@code seq}.
     *
     * <p>Keyset pagination over the unique {@code seq} index: the chain is
     * read in pages without ever materialising it, and without the
     * offset scan that a growing table would make progressively slower.
     */
    List<AuditEvent> findBySeqGreaterThanOrderBySeqAsc(long seq, Pageable pageable);

    List<AuditEvent> findByProposalKeyAndRevisionNumberOrderBySeqAsc(UUID proposalKey, int revisionNumber);

    List<AuditEvent> findByProposalKeyOrderBySeqAsc(UUID proposalKey);
}
