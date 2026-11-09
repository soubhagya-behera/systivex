package com.soubhagya.systivex.evidence;

import com.soubhagya.systivex.intelligence.ImpactAnalysisService;
import com.soubhagya.systivex.intelligence.ImpactQueryValidator;
import com.soubhagya.systivex.intelligence.ImpactResponse;
import com.soubhagya.systivex.intelligence.RiskResponse;
import com.soubhagya.systivex.intelligence.RiskService;
import com.soubhagya.systivex.intelligence.SimulationOperation;
import com.soubhagya.systivex.intelligence.SimulationProposalValidator;
import com.soubhagya.systivex.intelligence.SimulationResponse;
import com.soubhagya.systivex.intelligence.SimulationService;
import com.soubhagya.systivex.policy.PolicyEvaluationResponse;
import com.soubhagya.systivex.policy.PolicyService;
import com.soubhagya.systivex.proposal.api.ProposalResponse;
import com.soubhagya.systivex.proposal.service.ProposalService;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import com.soubhagya.systivex.twin.service.TwinGraphSnapshot;
import com.soubhagya.systivex.twin.service.TwinService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic, read-only assembler for Phase 14 S1 evidence packets (D39).
 *
 * <p>The assembler runs in one {@code READ_COMMITTED} read-only transaction.
 * {@code READ_COMMITTED} does not provide a single globally consistent
 * snapshot across the independent source reads; source timestamps retain
 * their original per-source semantics and must not be read as one global
 * snapshot time. Source timestamps, counts, and qualifications are preserved
 * exactly as the owning services and Slice 2 adapters report them.
 */
@Service
public class EvidencePacketAssembler {

    private final TwinService twinService;
    private final ImpactAnalysisService impactService;
    private final RiskService riskService;
    private final SimulationService simulationService;
    private final PolicyService policyService;
    private final ProposalService proposalService;
    private final ObjectMapper objectMapper;

    private final TwinEntityEvidenceAdapter twinEntityAdapter = new TwinEntityEvidenceAdapter();
    private final TwinRelationshipEvidenceAdapter twinRelationshipAdapter =
            new TwinRelationshipEvidenceAdapter();
    private final RuntimeObservationEvidenceAdapter runtimeAdapter =
            new RuntimeObservationEvidenceAdapter();
    private final ImpactEvidenceAdapter impactAdapter = new ImpactEvidenceAdapter();
    private final RiskEvidenceAdapter riskAdapter = new RiskEvidenceAdapter();
    private final SimulationEvidenceAdapter simulationAdapter = new SimulationEvidenceAdapter();
    private final PolicyEvidenceAdapter policyAdapter = new PolicyEvidenceAdapter();
    private final ProposalEvidenceAdapter proposalAdapter = new ProposalEvidenceAdapter();
    private final AuditEvidenceAdapter auditAdapter = new AuditEvidenceAdapter();

    public EvidencePacketAssembler(
            TwinService twinService,
            ImpactAnalysisService impactService,
            RiskService riskService,
            SimulationService simulationService,
            PolicyService policyService,
            ProposalService proposalService,
            ObjectMapper objectMapper) {
        this.twinService = twinService;
        this.impactService = impactService;
        this.riskService = riskService;
        this.simulationService = simulationService;
        this.policyService = policyService;
        this.proposalService = proposalService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public EvidencePacket assemble(EvidencePacketRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Evidence packet request is required");
        }
        List<EvidenceItem<?>> items = new ArrayList<>();
        List<EvidenceOmission> omissions = new ArrayList<>();

        TwinGraphSnapshot snapshot = twinService.readGraphSnapshot();
        if (snapshot == null || !snapshot.availableForAnalysis()) {
            String reason = graphUnavailableReason(snapshot);
            for (String source : List.of(
                    "twin.entity",
                    "twin.relationship",
                    "twin.runtime",
                    "impact.analysis",
                    "risk.assessment",
                    "change.simulation",
                    "policy.evaluation")) {
                omissions.add(new EvidenceOmission(
                        source, reason, EvidenceSourceStatus.UNAVAILABLE));
            }
            addProposalAndAudit(request, items, omissions);
            return finalizePacket(request.mode(), items, omissions);
        }

        Map<UUID, SystemEntity> entitiesById = new LinkedHashMap<>();
        for (SystemEntity entity : snapshot.entities()) {
            if (entity != null && entity.getId() != null) {
                entitiesById.put(entity.getId(), entity);
            }
        }
        ImpactQueryValidator.ParsedInputs inputs = ImpactQueryValidator.parseSnapshotInputs(
                entitiesById, request.from(), request.maxDepth(), request.includeTypes());
        Set<UUID> rootIds = new HashSet<>();
        for (SystemEntity start : inputs.starts()) {
            if (!rootIds.add(start.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate starting entity: "
                                + (start.getExternalRef() != null
                                        ? start.getExternalRef()
                                        : String.valueOf(start.getId())));
            }
        }

        items.addAll(twinEntityAdapter.adaptAll(snapshot.entities()));
        items.addAll(twinRelationshipAdapter.adaptAll(snapshot.relationships()));
        for (SystemEntity entity : snapshot.entities()) {
            if (entity != null) {
                items.addAll(runtimeAdapter.adapt(entity));
            }
        }

        switch (request.mode()) {
            case BASELINE -> {
                ImpactResponse impact = impactService.analyzeResolved(
                        inputs.starts(),
                        inputs.maxDepth(),
                        inputs.includeTypes(),
                        snapshot.entities(),
                        snapshot.relationships());
                items.addAll(impactAdapter.adapt(impact));
                RiskResponse risk = riskService.assessResolved(
                        inputs.starts(),
                        inputs.maxDepth(),
                        inputs.includeTypes(),
                        snapshot.entities(),
                        snapshot.relationships());
                items.addAll(riskAdapter.adapt(risk));
                omissions.add(new EvidenceOmission(
                        "change.simulation",
                        "Simulation evidence is not applicable to BASELINE mode.",
                        EvidenceSourceStatus.OMITTED));
                omissions.add(new EvidenceOmission(
                        "policy.evaluation",
                        "Policy evidence is not applicable to BASELINE mode.",
                        EvidenceSourceStatus.OMITTED));
            }
            case SIMULATION, POLICY_EVALUATION -> {
                SimulationResponse simulation = runSimulation(request, inputs, snapshot, entitiesById);
                items.addAll(simulationAdapter.adapt(simulation));
                if (request.mode() == EvidenceMode.SIMULATION) {
                    omissions.add(new EvidenceOmission(
                            "policy.evaluation",
                            "Policy evidence is not included in SIMULATION mode;"
                                    + " request POLICY_EVALUATION separately.",
                            EvidenceSourceStatus.OMITTED));
                } else {
                    PolicyEvaluationResponse policy = policyService.evaluateResolved(simulation);
                    items.addAll(policyAdapter.adapt(policy));
                }
            }
        }

        addProposalAndAudit(request, items, omissions);
        return finalizePacket(request.mode(), items, omissions);
    }

    public byte[] serialize(EvidencePacket packet) {
        if (packet == null) {
            throw new IllegalArgumentException("Evidence packet is required");
        }
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(packet);
        } catch (RuntimeException e) {
            throw new EvidenceAssemblyException(
                    EvidenceAssemblyException.SERIALIZATION_FAILED,
                    "Evidence packet serialization failed: " + e.getMessage(),
                    e);
        }
        if (bytes.length > packet.limits().maxSerializedBytes()) {
            throw new EvidenceAssemblyException(
                    EvidenceAssemblyException.PACKET_TOO_LARGE,
                    "Evidence packet serialized size " + bytes.length
                            + " bytes exceeds the limit of "
                            + packet.limits().maxSerializedBytes() + " bytes");
        }
        return bytes;
    }

    private SimulationResponse runSimulation(
            EvidencePacketRequest request,
            ImpactQueryValidator.ParsedInputs inputs,
            TwinGraphSnapshot snapshot,
            Map<UUID, SystemEntity> entitiesById) {
        List<SimulationOperation> operations = request.operations();
        if (operations.size() > EvidenceLimits.MAX_SIMULATION_OPERATIONS) {
            throw new IllegalArgumentException(
                    "At most " + EvidenceLimits.MAX_SIMULATION_OPERATIONS
                            + " simulation operations are supported");
        }
        Set<UUID> rootIds = new HashSet<>();
        for (SystemEntity start : inputs.starts()) {
            rootIds.add(start.getId());
        }
        SimulationProposalValidator.ResolvedProposal proposal =
                SimulationProposalValidator.validate(
                        operations,
                        rootIds,
                        existingEdgeKeys(snapshot.relationships()),
                        raw -> resolveSnapshot(entitiesById, raw));
        return simulationService.simulateResolved(
                inputs, snapshot.entities(), snapshot.relationships(), proposal);
    }

    private void addProposalAndAudit(
            EvidencePacketRequest request,
            List<EvidenceItem<?>> items,
            List<EvidenceOmission> omissions) {
        if (request.proposalKey() == null) {
            omissions.add(new EvidenceOmission(
                    "proposal.revision",
                    "Proposal evidence was not requested.",
                    EvidenceSourceStatus.OMITTED));
        } else {
            try {
                ProposalResponse proposal = request.revisionNumber() != null
                        ? proposalService.revision(request.proposalKey(), request.revisionNumber())
                        : proposalService.latest(request.proposalKey());
                items.addAll(proposalAdapter.adapt(proposal));
            } catch (EntityNotFoundException e) {
                omissions.add(new EvidenceOmission(
                        "proposal.revision",
                        nonBlank(e.getMessage(), "Proposal evidence is unavailable."),
                        EvidenceSourceStatus.UNAVAILABLE));
            }
        }
        if (!request.includeAuditEvidence()) {
            omissions.add(new EvidenceOmission(
                    "audit.events",
                    "Audit evidence was not requested.",
                    EvidenceSourceStatus.OMITTED));
        } else {
            try {
                items.addAll(auditAdapter.adapt(
                        proposalService.boundedAuditTrail(request.proposalKey())));
            } catch (EntityNotFoundException e) {
                omissions.add(new EvidenceOmission(
                        "audit.events",
                        nonBlank(e.getMessage(), "Audit evidence is unavailable."),
                        EvidenceSourceStatus.UNAVAILABLE));
            }
        }
    }

    private EvidencePacket finalizePacket(
            EvidenceMode mode,
            List<EvidenceItem<?>> items,
            List<EvidenceOmission> omissions) {
        List<EvidenceItem<?>> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt((EvidenceItem<?> item) -> item.category().ordinal())
                .thenComparing(EvidenceItem::source));
        Map<String, Integer> totals = new HashMap<>();
        for (EvidenceItem<?> item : sorted) {
            totals.merge(item.source(), 1, Integer::sum);
        }
        Map<String, Integer> kept = new HashMap<>();
        List<EvidenceItem<?>> capped = new ArrayList<>();
        for (EvidenceItem<?> item : sorted) {
            int count = kept.merge(item.source(), 1, Integer::sum);
            if (count > EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY) {
                continue;
            }
            if (count == EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY
                    && totals.get(item.source()) > EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY) {
                int omitted = totals.get(item.source()) - EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY;
                item = item.withTruncation(
                        EvidenceSourceStatus.TRUNCATED,
                        EvidenceQualification.TRUNCATED,
                        List.of("Evidence source '" + item.source() + "' capped at "
                                + EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY + "; "
                                + omitted + " items omitted."),
                        EvidenceLimits.MAX_ITEMS_PER_SOURCE_FAMILY,
                        omitted);
            }
            capped.add(item);
        }
        List<EvidenceItem<?>> referenced = new ArrayList<>();
        for (int i = 0; i < capped.size(); i++) {
            referenced.add(capped.get(i).withRef(EvidenceReference.of(i + 1)));
        }
        EvidencePacket packet = new EvidencePacket(
                EvidenceReference.packet(1),
                mode,
                referenced,
                EvidencePacket.EvidencePacketLimits.defaults(),
                omissions);
        serialize(packet);
        return packet;
    }

    private static String graphUnavailableReason(TwinGraphSnapshot snapshot) {
        if (snapshot == null) {
            return "Bounded graph snapshot is unavailable.";
        }
        List<String> limitations = new ArrayList<>();
        for (String limitation : snapshot.limitations()) {
            if (limitation != null && !limitation.isBlank()) {
                limitations.add(limitation);
            }
        }
        if (!limitations.isEmpty()) {
            return String.join("; ", limitations);
        }
        return "Bounded graph snapshot is not available for analysis (truncated="
                + snapshot.truncated() + ", consistent=" + snapshot.consistent() + ").";
    }

    private static Set<String> existingEdgeKeys(List<SystemRelationship> relationships) {
        Set<String> keys = new HashSet<>();
        for (SystemRelationship relationship : relationships) {
            if (relationship == null
                    || relationship.getSource() == null
                    || relationship.getTarget() == null
                    || relationship.getRelationshipType() == null) {
                continue;
            }
            keys.add(relationship.getSource().getId()
                    + "|" + relationship.getTarget().getId()
                    + "|" + relationship.getRelationshipType().name());
        }
        return keys;
    }

    private static UUID resolveSnapshot(Map<UUID, SystemEntity> entitiesById, String raw) {
        String trimmed = raw.trim();
        UUID id = tryParseUuid(trimmed);
        if (id != null && entitiesById.containsKey(id)) {
            return id;
        }
        for (SystemEntity entity : entitiesById.values()) {
            if (trimmed.equals(entity.getExternalRef())) {
                return entity.getId();
            }
        }
        throw new EntityNotFoundException("SystemEntity not found: " + raw);
    }

    private static UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
