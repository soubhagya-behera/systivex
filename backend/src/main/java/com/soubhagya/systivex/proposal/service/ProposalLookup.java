package com.soubhagya.systivex.proposal.service;

import com.soubhagya.systivex.proposal.repository.ProposalRevisionRepository;
import com.soubhagya.systivex.proposal.model.ProposalRevision;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * Shared revision lookups for governed proposal services (Phase 11 S4).
 * Every unknown key or revision fails 404 through the existing handler.
 */
public final class ProposalLookup {

    private ProposalLookup() {}

    public static ProposalRevision latestOrThrow(
            ProposalRevisionRepository revisions, UUID proposalKey) {
        return revisions
                .findFirstByProposalKeyOrderByRevisionNumberDesc(proposalKey)
                .orElseThrow(
                        () -> new EntityNotFoundException("Proposal not found: " + proposalKey));
    }
}
