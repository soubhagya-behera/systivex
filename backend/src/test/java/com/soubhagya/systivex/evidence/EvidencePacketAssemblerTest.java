package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.intelligence.ImpactAnalysisService;
import com.soubhagya.systivex.intelligence.ImpactResponse;
import com.soubhagya.systivex.intelligence.RiskResponse;
import com.soubhagya.systivex.intelligence.RiskService;
import com.soubhagya.systivex.intelligence.SimulationDelta;
import com.soubhagya.systivex.intelligence.SimulationGraphSummary;
import com.soubhagya.systivex.intelligence.SimulationOperation;
import com.soubhagya.systivex.intelligence.SimulationOperationType;
import com.soubhagya.systivex.intelligence.SimulationRequest;
import com.soubhagya.systivex.intelligence.SimulationResponse;
import com.soubhagya.systivex.intelligence.SimulationService;
import com.soubhagya.systivex.intelligence.SimulationSettings;
import com.soubhagya.systivex.policy.PolicyDecision;
import com.soubhagya.systivex.policy.PolicyEvaluationResponse;
import com.soubhagya.systivex.policy.PolicyService;
import com.soubhagya.systivex.proposal.api.AuditEventResponse;
import com.soubhagya.systivex.proposal.api.ProposalResponse;
import com.soubhagya.systivex.proposal.service.AuditService;
import com.soubhagya.systivex.proposal.service.ProposalService;
import com.soubhagya.systivex.twin.model.SystemEntity;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationship;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import com.soubhagya.systivex.twin.service.EntityNotFoundException;
import com.soubhagya.systivex.twin.service.TwinGraphSnapshot;
import com.soubhagya.systivex.twin.service.TwinService;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class EvidencePacketAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID PROPOSAL_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TwinService twinService;
    private ImpactAnalysisService impactService;
    private RiskService riskService;
    private SimulationService simulationService;
    private PolicyService policyService;
    private ProposalService proposalService;
    private EvidencePacketAssembler assembler;

    private SystemEntity svcA;
    private SystemEntity svcB;
    private SystemEntity table;
    private List<SystemEntity> entities;
    private List<SystemRelationship> relationships;
    private ImpactResponse impact;
    private RiskResponse risk;
    private SimulationResponse simulation;
    private PolicyEvaluationResponse policy;

    @BeforeEach
    void setUp() {
        twinService = mock(TwinService.class);
        impactService = mock(ImpactAnalysisService.class);
        riskService = mock(RiskService.class);
        simulationService = mock(SimulationService.class);
        policyService = mock(PolicyService.class);
        proposalService = mock(ProposalService.class);
        assembler = new EvidencePacketAssembler(
                twinService,
                impactService,
                riskService,
                simulationService,
                policyService,
                proposalService,
                JsonMapper.builder().build());

        svcA = entity("svc-a", SystemEntityType.SERVICE);
        svcB = entity("svc-b", SystemEntityType.SERVICE);
        table = entity("tbl-1", SystemEntityType.TABLE);
        entities = List.of(svcA, svcB, table);
        relationships = List.of(new SystemRelationship(svcA, table, SystemRelationshipType.DEPENDS_ON));
        impact = new ImpactResponse(
                List.of(new ImpactResponse.StartEntity(
                        svcA.getId(), SystemEntityType.SERVICE, "svc-a", "svc-a", "prod")),
                List.of(),
                List.of(),
                6,
                1,
                0,
                false,
                null,
                false,
                NOW,
                3,
                1,
                new ImpactResponse.Limits(10, 10, 500, 2000),
                "note");
        risk = new RiskResponse(
                List.of(), List.of(), 6, "risk-v1", NOW, 3, 1, "note");
        SimulationSettings settings = new SimulationSettings(
                6, EnumSet.of(SystemEntityType.SERVICE, SystemEntityType.TABLE), 1);
        SimulationDelta delta = new SimulationDelta(
                0, 0, 0, 0, List.of(), List.of(), 0, 0, 0, 0, List.of(), 0, 0, false);
        SimulationGraphSummary graph = new SimulationGraphSummary(3, 3, 1, 1, 0, 0, 0);
        simulation = new SimulationResponse(
                true,
                "SIMULATED — NOT EXECUTED",
                settings,
                new SimulationResponse.Scope(impact, risk),
                new SimulationResponse.Scope(impact, risk),
                graph,
                delta,
                List.of("SIMULATED — NOT EXECUTED"));
        policy = new PolicyEvaluationResponse(
                PolicyDecision.ALLOW,
                "policy-v1",
                "ADVISORY POLICY EVALUATION — NOT AN AUTHORIZATION; NOT EXECUTED",
                settings,
                List.of(),
                new PolicyEvaluationResponse.SimulationSummary(graph, delta, List.of()),
                "note");

        when(twinService.readGraphSnapshot()).thenReturn(snapshot(entities, relationships));
        when(impactService.analyzeResolved(any(), anyInt(), any(), any(), any())).thenReturn(impact);
        when(riskService.assessResolved(any(), anyInt(), any(), any(), any())).thenReturn(risk);
        when(simulationService.simulateResolved(any(), any(), any(), any())).thenReturn(simulation);
        when(policyService.evaluateResolved(any())).thenReturn(policy);
    }

    @Test
    void requestValidationRejectsInvalidModesRootsDepthAndOptions() {
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        null, List.of("svc-a"), null, List.of(), List.of(), null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of(" "), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("r" + i);
        }
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, eleven, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 10");
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of("svc-a", " svc-a "), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of("svc-a"), 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of("svc-a"), 11, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of("NOPE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown entity type");
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        EvidenceMode.BASELINE,
                        List.of("svc-a"),
                        null,
                        List.of(),
                        List.of(new SimulationOperation(
                                SimulationOperationType.REMOVE_ENTITY, "tbl-1", null, null, null)),
                        null,
                        null,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operations are only allowed");
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                        null, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                        null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullOperationElementIsRejectedWithIllegalArgumentException() {
        List<SimulationOperation> operations = new ArrayList<>();
        operations.add(null);
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        EvidenceMode.SIMULATION, List.of("svc-a"), null, List.of(),
                        operations, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operation type is required");
    }

    @Test
    void baselineUsesBoundedSnapshotAndResolvedMethodsOnly() {
        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        verify(twinService).readGraphSnapshot();
        verify(twinService, never()).listEntities();
        verify(twinService, never()).listRelationships();
        verify(impactService).analyzeResolved(any(), eq(6), any(), eq(entities), eq(relationships));
        verify(riskService).assessResolved(any(), eq(6), any(), eq(entities), eq(relationships));
        verify(impactService, never()).analyze(any(), any(), any());
        verify(riskService, never()).assess(any(), any(), any());
        verifyNoInteractions(simulationService, policyService);

        assertThat(packet.packetRef().value()).isEqualTo("P1");
        assertThat(packet.mode()).isEqualTo(EvidenceMode.BASELINE);
        assertThat(packet.items()).extracting(EvidenceItem::source)
                .contains("twin.entity", "twin.relationship", "impact.analysis", "risk.assessment");
        assertThat(packet.items()).extracting(item -> item.ref().value())
                .containsExactlyElementsOf(expectedRefs(packet.items().size()));
        assertThat(packet.omissions()).extracting(EvidenceOmission::source)
                .contains("change.simulation", "policy.evaluation", "proposal.revision", "audit.events");
        assertThat(packet.omissions()).allMatch(o -> o.status() == EvidenceSourceStatus.OMITTED);
        EvidenceItem<?> impactItem = bySource(packet, "impact.analysis");
        assertThat(impactItem.category()).isEqualTo(EvidenceCategory.DETERMINISTIC_FINDING);
        assertThat(impactItem.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
    }

    @Test
    void referencesAreDeterministicAcrossAssemblies() {
        EvidencePacket first = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));
        EvidencePacket second = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        assertThat(second.items()).extracting(EvidenceItem::ref)
                .containsExactlyElementsOf(first.items().stream().map(EvidenceItem::ref).toList());
        assertThat(second.items()).extracting(EvidenceItem::source)
                .containsExactlyElementsOf(first.items().stream().map(EvidenceItem::source).toList());
        assertThat(second.items()).extracting(EvidenceItem::scope)
                .containsExactlyElementsOf(first.items().stream().map(EvidenceItem::scope).toList());
    }

    @Test
    void depthBoundariesAndDefaultArePreserved() {
        assembler.assemble(request(EvidenceMode.BASELINE, List.of("svc-a"), 1, List.of()));
        assembler.assemble(request(EvidenceMode.BASELINE, List.of("svc-a"), 10, List.of()));
        assembler.assemble(request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        ArgumentCaptor<Integer> depth = ArgumentCaptor.forClass(Integer.class);
        verify(impactService, org.mockito.Mockito.times(3))
                .analyzeResolved(any(), depth.capture(), any(), any(), any());
        assertThat(depth.getAllValues()).containsExactly(1, 10, 6);
    }

    @Test
    void tenRootsAccepted() {
        List<SystemEntity> many = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SystemEntity e = entity("root-" + i, SystemEntityType.SERVICE);
            many.add(e);
            roots.add("root-" + i);
        }
        when(twinService.readGraphSnapshot()).thenReturn(snapshot(many, List.of()));

        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, roots, null, List.of()));

        assertThat(packet.items()).extracting(EvidenceItem::source)
                .contains("impact.analysis", "risk.assessment");
        verify(impactService).analyzeResolved(any(), anyInt(), any(), eq(many), eq(List.of()));
    }

    @Test
    void unknownRootPropagatesAndSkipsAnalysis() {
        assertThatThrownBy(() -> assembler.assemble(
                        request(EvidenceMode.BASELINE, List.of("missing"), null, List.of())))
                .isInstanceOf(EntityNotFoundException.class);
        verify(impactService, never()).analyzeResolved(any(), anyInt(), any(), any(), any());
        verify(riskService, never()).assessResolved(any(), anyInt(), any(), any(), any());
    }

    @Test
    void duplicateResolvedRootIsRejected() {
        String byId = svcA.getId().toString();
        assertThatThrownBy(() -> assembler.assemble(
                        request(EvidenceMode.BASELINE, List.of("svc-a", byId), null, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate starting entity");
        verify(impactService, never()).analyzeResolved(any(), anyInt(), any(), any(), any());
    }

    @Test
    void truncatedGraphProducesUnavailableOmissionsAndNoAnalysis() {
        TwinGraphSnapshot truncated = new TwinGraphSnapshot(
                entities, relationships, 501, 1, true, false, true,
                List.of("entity count exceeded 500; graph snapshot truncated and analysis refused"));
        when(twinService.readGraphSnapshot()).thenReturn(truncated);

        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        assertThat(packet.items()).isEmpty();
        assertThat(packet.omissions()).extracting(EvidenceOmission::source)
                .contains("twin.entity", "twin.relationship", "twin.runtime",
                        "impact.analysis", "risk.assessment");
        assertThat(packet.omissions())
                .filteredOn(o -> o.source().equals("impact.analysis"))
                .allMatch(o -> o.status() == EvidenceSourceStatus.UNAVAILABLE
                        && o.reason().contains("truncated"));
        verify(impactService, never()).analyzeResolved(any(), anyInt(), any(), any(), any());
        verify(riskService, never()).assessResolved(any(), anyInt(), any(), any(), any());
        verifyNoInteractions(simulationService, policyService);
    }

    @Test
    void inconsistentGraphProducesUnavailableOmissionsAndNoAnalysis() {
        TwinGraphSnapshot inconsistent = new TwinGraphSnapshot(
                entities, relationships, 3, 1, false, false, false,
                List.of("relationship endpoint missing from accepted entity snapshot;"
                        + " graph analysis refused"));
        when(twinService.readGraphSnapshot()).thenReturn(inconsistent);

        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        assertThat(packet.items()).isEmpty();
        assertThat(packet.omissions())
                .allMatch(o -> o.status() == EvidenceSourceStatus.UNAVAILABLE
                        || o.status() == EvidenceSourceStatus.OMITTED);
        verify(impactService, never()).analyzeResolved(any(), anyInt(), any(), any(), any());
        verify(riskService, never()).assessResolved(any(), anyInt(), any(), any(), any());
    }

    @Test
    void simulationModeUsesResolvedSimulationAndNeverPolicy() {
        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.SIMULATION,
                List.of("svc-a"),
                null,
                List.of(),
                List.of(new SimulationOperation(
                        SimulationOperationType.REMOVE_ENTITY, "tbl-1", null, null, null)),
                null,
                null,
                false));

        verify(simulationService).simulateResolved(any(), eq(entities), eq(relationships), any());
        verify(simulationService, never()).simulate(any());
        verifyNoInteractions(policyService);
        EvidenceItem<?> item = bySource(packet, "change.simulation");
        assertThat(item.category()).isEqualTo(EvidenceCategory.HYPOTHETICAL_RESULT);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.HYPOTHETICAL);
        assertThat(packet.omissions()).extracting(EvidenceOmission::source)
                .contains("policy.evaluation");
    }

    @Test
    void policyModeUsesResolvedSimulationThenResolvedPolicy() {
        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.POLICY_EVALUATION,
                List.of("svc-a"),
                null,
                List.of(),
                List.of(new SimulationOperation(
                        SimulationOperationType.REMOVE_ENTITY, "tbl-1", null, null, null)),
                null,
                null,
                false));

        verify(simulationService).simulateResolved(any(), eq(entities), eq(relationships), any());
        verify(simulationService, never()).simulate(any());
        verify(policyService).evaluateResolved(eq(simulation));
        verify(policyService, never()).evaluate(any(SimulationRequest.class));
        EvidenceItem<?> simItem = bySource(packet, "change.simulation");
        assertThat(simItem.category()).isEqualTo(EvidenceCategory.HYPOTHETICAL_RESULT);
        EvidenceItem<?> policyItem = bySource(packet, "policy.evaluation");
        assertThat(policyItem.qualification()).isEqualTo(EvidenceQualification.ADVISORY);
        assertThat(((PolicyEvidence) policyItem.content()).decision())
                .isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void moreThan100OperationsRejectedBeforeSimulationService() {
        List<SimulationOperation> operations = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            operations.add(new SimulationOperation(
                    SimulationOperationType.REMOVE_ENTITY, "tbl-1", null, null, null));
        }
        assertThatThrownBy(() -> new EvidencePacketRequest(
                        EvidenceMode.SIMULATION, List.of("svc-a"), null, List.of(),
                        operations, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 100");
        verifyNoInteractions(twinService, simulationService, policyService);
    }

    @Test
    void proposalLatestOnlyLookupPreservesQualification() {
        when(proposalService.latest(PROPOSAL_KEY)).thenReturn(proposal("DRAFT"));

        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                PROPOSAL_KEY, null, false));

        verify(proposalService).latest(PROPOSAL_KEY);
        verify(proposalService, never()).revision(any(), anyInt());
        verify(proposalService, never()).history(any());
        EvidenceItem<?> item = bySource(packet, "proposal.revision");
        assertThat(item.category()).isEqualTo(EvidenceCategory.GOVERNANCE_RECORD);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
        assertThat(item.timestamps()).extracting(EvidenceTimestamp::name).contains("expiresAt");
    }

    @Test
    void proposalSelectedRevisionLookup() {
        when(proposalService.revision(PROPOSAL_KEY, 2)).thenReturn(proposal("SUPERSEDED"));

        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                PROPOSAL_KEY, 2, false));

        verify(proposalService).revision(PROPOSAL_KEY, 2);
        verify(proposalService, never()).latest(any());
        assertThat(bySource(packet, "proposal.revision").qualification())
                .isEqualTo(EvidenceQualification.HISTORICAL);
    }

    @Test
    void missingProposalIsRecordedAsUnavailableOmission() {
        when(proposalService.latest(PROPOSAL_KEY))
                .thenThrow(new EntityNotFoundException("Proposal not found: " + PROPOSAL_KEY));

        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                PROPOSAL_KEY, null, false));

        assertThat(packet.items()).extracting(EvidenceItem::source)
                .doesNotContain("proposal.revision");
        assertThat(packet.omissions())
                .filteredOn(o -> o.source().equals("proposal.revision"))
                .allMatch(o -> o.status() == EvidenceSourceStatus.UNAVAILABLE);
        assertThat(packet.items()).extracting(EvidenceItem::source).contains("impact.analysis");
    }

    @Test
    void boundedAuditRetrievalPreservesExactOmittedCount() {
        List<AuditEventResponse> events = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            events.add(new AuditEventResponse(
                    UUID.randomUUID(), i, NOW, "issuer", "subject", "CREATED", "SUCCESS",
                    PROPOSAL_KEY, 1, "ops-hash", "assess-hash", "reason", "corr",
                    "prev", "hash"));
        }
        when(proposalService.boundedAuditTrail(PROPOSAL_KEY))
                .thenReturn(new ProposalService.BoundedAuditTrail(events, true, 100, 250));

        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                PROPOSAL_KEY, null, true));

        verify(proposalService).boundedAuditTrail(PROPOSAL_KEY);
        verify(proposalService, never()).auditTrail(any());
        EvidenceItem<?> item = bySource(packet, "audit.events");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(250);
        assertThat(item.limitations()).anyMatch(v -> v.contains("250"));
    }

    @Test
    void emptyAuditIsNotPresentedAsSuccessfulEvidence() {
        when(proposalService.boundedAuditTrail(PROPOSAL_KEY))
                .thenReturn(new ProposalService.BoundedAuditTrail(List.of(), false, 100, 0));

        EvidencePacket packet = assembler.assemble(new EvidencePacketRequest(
                EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                PROPOSAL_KEY, null, true));

        EvidenceItem<?> item = bySource(packet, "audit.events");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.MISSING);
    }

    @Test
    void assemblerNeverReferencesGlobalAuditVerification() {
        for (Field field : EvidencePacketAssembler.class.getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(AuditService.class);
        }
    }

    @Test
    void runtimeSourceFamilyIsCappedAt100WithExplicitTruncation() {
        List<SystemEntity> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(runtimeEntity(String.format("rt-%03d", i)));
        }
        when(twinService.readGraphSnapshot()).thenReturn(snapshot(many, List.of()));

        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("rt-000"), null, List.of()));

        List<EvidenceItem<?>> runtimeItems = packet.items().stream()
                .filter(item -> item.source().equals("twin.runtime"))
                .toList();
        assertThat(runtimeItems).hasSize(100);
        EvidenceItem<?> last = runtimeItems.get(runtimeItems.size() - 1);
        assertThat(last.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(last.omittedCount()).isEqualTo(1);
        assertThat(last.limitations()).anyMatch(v -> v.contains("capped at 100"));
    }

    @Test
    void packetValidationEnforcesPerSourceAndTotalItemBoundaries() {
        List<EvidenceItem<?>> hundred = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            hundred.add(item("src-a", i));
        }
        assertThat(new EvidencePacket(
                        EvidenceReference.packet(1), EvidenceMode.BASELINE, hundred,
                        EvidencePacket.EvidencePacketLimits.defaults(), List.of()))
                .isNotNull();

        List<EvidenceItem<?>> hundredOne = new ArrayList<>(hundred);
        hundredOne.add(item("src-a", 101));
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1), EvidenceMode.BASELINE, hundredOne,
                        EvidencePacket.EvidencePacketLimits.defaults(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source family");

        List<EvidenceItem<?>> thousand = new ArrayList<>();
        for (int s = 0; s < 10; s++) {
            for (int i = 1; i <= 100; i++) {
                thousand.add(item("src-" + s, thousand.size() + 1));
            }
        }
        assertThat(new EvidencePacket(
                        EvidenceReference.packet(1), EvidenceMode.BASELINE, thousand,
                        EvidencePacket.EvidencePacketLimits.defaults(), List.of()))
                .isNotNull();

        List<EvidenceItem<?>> thousandOne = new ArrayList<>(thousand);
        thousandOne.add(item("src-10", 1001));
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1), EvidenceMode.BASELINE, thousandOne,
                        EvidencePacket.EvidencePacketLimits.defaults(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total item limit");
    }

    @Test
    void oversizedPacketFailsWithPacketTooLargeWithoutTruncatedJson() {
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode operations = mapper.createObjectNode();
        ArrayNode removed = operations.putArray("removedEntities");
        removed.add("x".repeat(300_000));
        ProposalResponse huge = proposalWithOperations(operations);
        when(proposalService.latest(PROPOSAL_KEY)).thenReturn(huge);

        assertThatThrownBy(() -> assembler.assemble(new EvidencePacketRequest(
                        EvidenceMode.BASELINE, List.of("svc-a"), null, List.of(), List.of(),
                        PROPOSAL_KEY, null, false)))
                .isInstanceOf(EvidenceAssemblyException.class)
                .satisfies(e -> assertThat(((EvidenceAssemblyException) e).code())
                        .isEqualTo(EvidenceAssemblyException.PACKET_TOO_LARGE));
    }

    @Test
    void serializedPacketIsMeasuredAgainstActuallyUtf8Bytes() {
        EvidencePacket packet = assembler.assemble(
                request(EvidenceMode.BASELINE, List.of("svc-a"), null, List.of()));

        byte[] bytes = assembler.serialize(packet);
        assertThat(bytes.length).isGreaterThan(0);
        assertThat(bytes.length).isLessThanOrEqualTo(EvidenceLimits.MAX_SERIALIZED_BYTES);
        assertThat(packet.limits().maxSerializedBytes()).isEqualTo(262144);
    }

    @Test
    void assembleIsDeclaredReadOnlyReadCommitted() throws Exception {
        Transactional tx = EvidencePacketAssembler.class
                .getMethod("assemble", EvidencePacketRequest.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
        assertThat(tx.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private static EvidencePacketRequest request(
            EvidenceMode mode, List<String> from, Integer maxDepth, List<String> includeTypes) {
        return new EvidencePacketRequest(
                mode, from, maxDepth, includeTypes, List.of(), null, null, false);
    }

    private static TwinGraphSnapshot snapshot(
            List<SystemEntity> entities, List<SystemRelationship> relationships) {
        return new TwinGraphSnapshot(
                entities, relationships, entities.size(), relationships.size(),
                false, true, true, List.of());
    }

    private static EvidenceItem<?> bySource(EvidencePacket packet, String source) {
        return packet.items().stream()
                .filter(item -> item.source().equals(source))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing source " + source));
    }

    private static List<String> expectedRefs(int count) {
        List<String> refs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            refs.add("E" + i);
        }
        return refs;
    }

    private static EvidenceItem<String> item(String source, int index) {
        return new EvidenceItem<>(
                EvidenceReference.of(index),
                EvidenceCategory.OBSERVED_FACT,
                source,
                "scope",
                null,
                List.of(),
                "content",
                List.of(),
                EvidenceQualification.CURRENT,
                EvidenceSourceStatus.AVAILABLE,
                1,
                0);
    }

    private static SystemEntity entity(String ref, SystemEntityType type) {
        return entity(ref, type, Map.of());
    }

    private static SystemEntity runtimeEntity(String ref) {
        Map<String, Object> runtime = Map.of(
                "managedBy", "telemetry-connector",
                "observedAt", NOW.toString(),
                "health", Map.of("available", true, "status", "UP"),
                "counters", Map.of(),
                "limitations", List.of());
        return entity(ref, SystemEntityType.SERVICE,
                Map.of("managedBy", "repository-connector", "runtime", runtime));
    }

    private static SystemEntity entity(
            String ref, SystemEntityType type, Map<String, Object> metadata) {
        SystemEntity entity = new SystemEntity(type, ref, ref, "prod", metadata);
        try {
            Field id = SystemEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(entity, UUID.nameUUIDFromBytes(ref.getBytes(StandardCharsets.UTF_8)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    private static ProposalResponse proposal(String status) {
        JsonMapper mapper = JsonMapper.builder().build();
        return proposalWithOperations(mapper.createObjectNode(), mapper.createObjectNode(), status);
    }

    private static ProposalResponse proposalWithOperations(JsonNode operations) {
        JsonMapper mapper = JsonMapper.builder().build();
        return proposalWithOperations(operations, mapper.createObjectNode(), "DRAFT");
    }

    private static ProposalResponse proposalWithOperations(
            JsonNode operations, JsonNode assessment, String status) {
        return new ProposalResponse(
                PROPOSAL_KEY,
                1,
                status,
                "title",
                "description",
                operations,
                "operations-hash",
                assessment,
                "assessment-hash",
                "ALLOW",
                "policy-v1",
                "risk-v1",
                "fingerprint",
                NOW,
                "proposer-issuer",
                "proposer-subject",
                "approver-issuer",
                "approver-subject",
                "reason",
                NOW.plusSeconds(3600),
                NOW,
                NOW);
    }
}
