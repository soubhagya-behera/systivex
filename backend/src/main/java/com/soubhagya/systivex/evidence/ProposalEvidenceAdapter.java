package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.proposal.api.ProposalResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class ProposalEvidenceAdapter implements EvidenceSource<ProposalResponse> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> OPERATION_FIELDS = List.of(
            "removedEntities", "addedRelationships", "removedRelationships");

    public ProposalEvidenceAdapter() {
    }

    @Override
    public List<EvidenceItem<?>> adapt(ProposalResponse proposal) {
        EvidenceItem<ProposalEvidence> item = adaptItem(proposal);
        return item == null ? List.of() : List.of(item);
    }

    public static int operationCount(JsonNode operations) {
        if (operations == null || operations.isNull() || !operations.isObject()) {
            return 0;
        }
        int count = 0;
        for (String field : OPERATION_FIELDS) {
            JsonNode value = operations.get(field);
            if (value != null && value.isArray()) {
                count += value.size();
            }
        }
        return count;
    }

    public static EvidenceItem<ProposalEvidence> adaptItem(ProposalResponse proposal) {
        if (proposal == null) {
            return null;
        }
        OperationView view = truncateOperations(
                proposal.operations(), EvidenceLimits.MAX_PROPOSAL_OPERATIONS);

        List<String> limitations = new ArrayList<>();
        if (view.total() > 0 && view.truncated()) {
            limitations.add("Proposal operations display-truncated at "
                    + EvidenceLimits.MAX_PROPOSAL_OPERATIONS + " of " + view.total()
                    + "; " + view.omitted() + " omitted; never used for simulation execution.");
        }
        if (proposal.operations() == null || proposal.operations().isNull()) {
            limitations.add("Proposal operations are unavailable.");
        }
        if (proposal.assessment() == null || proposal.assessment().isNull()) {
            limitations.add("Proposal assessment is unavailable.");
        }
        List<String> finalLimitations = EvidenceLimits.textList(
                limitations, EvidenceLimits.MAX_LIMITATIONS, EvidenceLimits.MAX_TEXT_LENGTH).values();
        boolean missingDocument = proposal.operations() == null
                || proposal.operations().isNull()
                || proposal.assessment() == null
                || proposal.assessment().isNull();

        ProposalEvidence content = toContent(proposal, view);
        EvidenceSourceStatus status;
        if (view.truncated()) {
            status = EvidenceSourceStatus.TRUNCATED;
        } else if (missingDocument) {
            status = EvidenceSourceStatus.UNAVAILABLE;
        } else {
            status = EvidenceSourceStatus.AVAILABLE;
        }
        return new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.GOVERNANCE_RECORD,
                "proposal.revision",
                "proposal:" + proposal.proposalKey(),
                proposal.policyVersion(),
                timestamps(content),
                content,
                finalLimitations,
                qualificationFor(proposal.status()),
                status,
                1 + view.included(),
                view.omitted());
    }

    public static ProposalEvidence toContent(ProposalResponse proposal) {
        return proposal == null
                ? null
                : toContent(
                        proposal,
                        truncateOperations(
                                proposal.operations(), EvidenceLimits.MAX_PROPOSAL_OPERATIONS));
    }

    private static ProposalEvidence toContent(ProposalResponse proposal, OperationView view) {
        return new ProposalEvidence(
                proposal.proposalKey(),
                proposal.revisionNumber(),
                proposal.status(),
                view.node(),
                view.total(),
                view.truncated(),
                view.included(),
                view.omitted(),
                proposal.operationsHash(),
                proposal.assessment(),
                proposal.assessmentHash(),
                proposal.policyDecision(),
                proposal.policyVersion(),
                proposal.riskTaxonomy(),
                proposal.twinFingerprint(),
                proposal.snapshotObservedAt(),
                proposal.expiresAt(),
                proposal.createdAt(),
                proposal.updatedAt());
    }

    static OperationView truncateOperations(JsonNode operations, int limit) {
        if (operations == null || operations.isNull()) {
            return new OperationView(null, 0, 0, 0);
        }
        if (!operations.isObject()) {
            return new OperationView(operations.deepCopy(), 0, 0, 0);
        }

        ObjectNode root = MAPPER.createObjectNode();
        int total = 0;
        int included = 0;
        for (String field : OPERATION_FIELDS) {
            JsonNode value = operations.get(field);
            if (value == null || !value.isArray()) {
                continue;
            }
            ArrayNode out = root.putArray(field);
            for (JsonNode element : value) {
                total++;
                if (included < limit) {
                    out.add(element.deepCopy());
                    included++;
                }
            }
        }
        int omitted = Math.max(0, total - included);
        return new OperationView(root, total, included, omitted);
    }

    private static List<EvidenceTimestamp> timestamps(ProposalEvidence content) {
        List<EvidenceTimestamp> timestamps = new ArrayList<>();
        if (content.createdAt() != null) {
            timestamps.add(new EvidenceTimestamp("createdAt", content.createdAt()));
        }
        if (content.updatedAt() != null) {
            timestamps.add(new EvidenceTimestamp("updatedAt", content.updatedAt()));
        }
        if (content.snapshotObservedAt() != null) {
            timestamps.add(new EvidenceTimestamp(
                    "snapshotObservedAt", content.snapshotObservedAt()));
        }
        if (content.expiresAt() != null) {
            timestamps.add(new EvidenceTimestamp("expiresAt", content.expiresAt()));
        }
        return timestamps;
    }

    static EvidenceQualification qualificationFor(String status) {
        return "SUPERSEDED".equalsIgnoreCase(status == null ? "" : status.trim())
                ? EvidenceQualification.HISTORICAL
                : EvidenceQualification.CURRENT;
    }

    record OperationView(JsonNode node, int total, int included, int omitted) {
        OperationView {
            if (total < 0 || included < 0 || omitted < 0) {
                throw new IllegalArgumentException("Operation counts must not be negative");
            }
        }

        boolean truncated() {
            return omitted > 0;
        }
    }
}
