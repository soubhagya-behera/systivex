package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.proposal.api.AuditEventResponse;
import com.soubhagya.systivex.proposal.api.ProposalResponse;
import com.soubhagya.systivex.proposal.service.ProposalService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ProposalAuditEvidenceAdapterTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID PROPOSAL_KEY = UUID.randomUUID();

    @Test
    void proposalEvidenceStructurallyExcludesSensitiveGovernanceFields() {
        assertThat(ProposalEvidence.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "title",
                        "description",
                        "reason",
                        "proposerIssuer",
                        "proposerSubject",
                        "approverIssuer",
                        "approverSubject",
                        "correlationId");
    }

    @Test
    void proposalAdapterCapsOperationsAt100AndFlagsTruncation() {
        ObjectNode operations = MAPPER.createObjectNode();
        ArrayNode removed = operations.putArray("removedEntities");
        for (int i = 0; i < 101; i++) {
            removed.add("entity-" + i);
        }
        ProposalResponse proposal = proposal(operations, MAPPER.createObjectNode());

        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(proposal);

        assertThat(item.category()).isEqualTo(EvidenceCategory.GOVERNANCE_RECORD);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.content().operationCount()).isEqualTo(101);
        assertThat(item.content().includedOperationCount()).isEqualTo(100);
        assertThat(item.content().omittedOperationCount()).isEqualTo(1);
        assertThat(item.content().operationsTruncated()).isTrue();
        assertThat(item.content().operations().get("removedEntities")).hasSize(100);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.limitations()).anyMatch(value -> value.contains("display-truncated"));
    }

    @Test
    void proposalAdapterCountsAllCanonicalOperationFields() {
        ObjectNode operations = MAPPER.createObjectNode();
        operations.putArray("removedEntities").add("entity");
        operations.putArray("addedRelationships").addObject()
                .put("source", "a").put("target", "b").put("relationshipType", "CALLS");
        operations.putArray("removedRelationships").addObject()
                .put("source", "c").put("target", "d").put("relationshipType", "READS");
        ProposalResponse proposal = proposal(operations, MAPPER.createObjectNode());

        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(proposal);

        assertThat(item.content().operationCount()).isEqualTo(3);
        assertThat(item.content().operations().propertyNames())
                .containsExactly("removedEntities", "addedRelationships", "removedRelationships");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
    }

    @Test
    void proposalAdapterDeepCopiesOperationsAndAssessment() {
        ObjectNode operations = MAPPER.createObjectNode();
        ArrayNode removed = operations.putArray("removedEntities");
        removed.add("entity-a");
        ObjectNode assessment = MAPPER.createObjectNode();
        assessment.put("score", 7);
        ProposalResponse proposal = proposal(operations, assessment);

        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(proposal);
        removed.add("entity-b");
        assessment.put("score", 99);

        assertThat(item.content().operations().get("removedEntities")).hasSize(1);
        assertThat(item.content().assessment().get("score").asInt()).isEqualTo(7);

        ((ArrayNode) item.content().operations().get("removedEntities")).add("entity-c");
        assertThat(item.content().operations().get("removedEntities")).hasSize(1);
    }

    @Test
    void proposalAdapterKeepsTimestampsAndReportsMissingDocuments() {
        ProposalResponse proposal = proposal(null, null);

        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(proposal);

        assertThat(item.timestamps()).extracting(EvidenceTimestamp::name)
                .containsExactly("createdAt", "updatedAt", "snapshotObservedAt", "expiresAt");
        assertThat(item.timestamps()).extracting(EvidenceTimestamp::value)
                .containsExactly(NOW, NOW, NOW, NOW.plusSeconds(3600));
        assertThat(item.content().expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.content().operationCount()).isZero();
        assertThat(item.content().operations()).isNull();
        assertThat(item.limitations()).anyMatch(value -> value.contains("operations are unavailable"));
        assertThat(item.limitations()).anyMatch(value -> value.contains("assessment is unavailable"));
    }

    @Test
    void proposalAdapterQualifiesLiveRevisionStatusesAsCurrent() {
        for (String status : List.of(
                "DRAFT", "ASSESSED", "AWAITING_APPROVAL", "APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED")) {
            EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(
                    proposal(status, MAPPER.createObjectNode(), MAPPER.createObjectNode()));
            assertThat(item.qualification())
                    .as("status %s", status)
                    .isEqualTo(EvidenceQualification.CURRENT);
            assertThat(item.content().status()).isEqualTo(status);
        }
    }

    @Test
    void proposalAdapterQualifiesSupersededRevisionAsHistorical() {
        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(
                proposal("SUPERSEDED", MAPPER.createObjectNode(), MAPPER.createObjectNode()));

        assertThat(item.qualification()).isEqualTo(EvidenceQualification.HISTORICAL);
        assertThat(item.content().status()).isEqualTo("SUPERSEDED");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
    }

    @Test
    void proposalAdapterPreservesExpiresAtInTimestampMapping() {
        EvidenceItem<ProposalEvidence> item = ProposalEvidenceAdapter.adaptItem(
                proposal("DRAFT", MAPPER.createObjectNode(), MAPPER.createObjectNode()));

        assertThat(item.content().expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(item.timestamps()).contains(
                new EvidenceTimestamp("expiresAt", NOW.plusSeconds(3600)));
    }

    @Test
    void auditAdapterSortsBySeqAndCapsAt100() {
        List<AuditEventResponse> events = new ArrayList<>();
        for (int i = 101; i >= 1; i--) {
            events.add(audit(i, NOW.plusSeconds(i)));
        }

        EvidenceItem<AuditEvidence> item = AuditEvidenceAdapter.adaptItem(events, false);

        assertThat(item.category()).isEqualTo(EvidenceCategory.AUDIT_EVIDENCE);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.HISTORICAL);
        assertThat(item.content().events()).hasSize(100);
        assertThat(item.content().events().get(0).seq()).isEqualTo(1L);
        assertThat(item.content().events().get(99).seq()).isEqualTo(100L);
        assertThat(item.content().omittedCount()).isEqualTo(1);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.limitations()).anyMatch(value -> value.contains("capped"));
    }

    @Test
    void auditAdapterDoesNotTruncateExactly100Events() {
        List<AuditEventResponse> events = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            events.add(audit(i, NOW.plusSeconds(i)));
        }

        EvidenceItem<AuditEvidence> item = AuditEvidenceAdapter.adaptItem(events, false);

        assertThat(item.content().events()).hasSize(100);
        assertThat(item.content().truncated()).isFalse();
        assertThat(item.content().omittedCount()).isZero();
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
    }

    @Test
    void auditAdapterEmptyIsUnavailableAndMissing() {
        EvidenceItem<AuditEvidence> item = AuditEvidenceAdapter.adaptItem(List.of(), false);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.UNAVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.MISSING);
        assertThat(item.content().events()).isEmpty();
        assertThat(item.includedCount()).isZero();
        assertThat(item.omittedCount()).isZero();
        assertThat(item.limitations()).anyMatch(value -> value.contains("No audit events"));
    }

    @Test
    void auditAdapterSkipsNullRowsAndSorts() {
        List<AuditEventResponse> events = new ArrayList<>();
        events.add(null);
        events.add(audit(2, NOW.plusSeconds(2)));
        events.add(audit(1, NOW.plusSeconds(1)));

        EvidenceItem<AuditEvidence> item = AuditEvidenceAdapter.adaptItem(events, false);

        assertThat(item.content().events()).hasSize(2);
        assertThat(item.content().events()).extracting(AuditEventEvidence::seq)
                .containsExactly(1L, 2L);
    }

    @Test
    void auditAdapterUsesExactOmittedHintFromBoundedTrail() {
        List<AuditEventResponse> events = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            events.add(audit(i, NOW.plusSeconds(i)));
        }
        ProposalService.BoundedAuditTrail bounded =
                new ProposalService.BoundedAuditTrail(events, true, 100, 250);

        List<EvidenceItem<?>> items = new AuditEvidenceAdapter().adapt(bounded);

        assertThat(items).hasSize(1);
        EvidenceItem<?> item = items.get(0);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(((AuditEvidence) item.content()).omittedCount()).isEqualTo(250);
        assertThat(item.limitations()).anyMatch(value -> value.contains("250"));

        EvidenceItem<AuditEvidence> hinted =
                AuditEvidenceAdapter.adaptItem(events, true, 250);
        assertThat(hinted.content().omittedCount()).isEqualTo(250);
    }

    @Test
    void auditEvidenceStructurallyExcludesIdentityAndReasonFields() {
        assertThat(AuditEventEvidence.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "actorIssuer",
                        "actorSubject",
                        "reason",
                        "correlationId",
                        "verified",
                        "chainValid");
    }

    @Test
    void auditAdapterReportsFirstAndLastOccurrenceTimes() {
        List<AuditEventResponse> events = List.of(
                audit(1, NOW),
                audit(2, NOW.plusSeconds(30)));

        EvidenceItem<AuditEvidence> item = AuditEvidenceAdapter.adaptItem(events, false);

        assertThat(item.timestamps()).extracting(EvidenceTimestamp::name)
                .containsExactly("firstOccurredAt", "lastOccurredAt");
        assertThat(item.timestamps()).extracting(EvidenceTimestamp::value)
                .containsExactly(NOW, NOW.plusSeconds(30));
    }

    private static ProposalResponse proposal(JsonNode operations, JsonNode assessment) {
        return proposal("DRAFT", operations, assessment);
    }

    private static ProposalResponse proposal(
            String status, JsonNode operations, JsonNode assessment) {
        return new ProposalResponse(
                PROPOSAL_KEY,
                3,
                status,
                "secret title",
                "secret description",
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
                "secret reason",
                NOW.plusSeconds(3600),
                NOW,
                NOW);
    }

    private static AuditEventResponse audit(long seq, Instant occurredAt) {
        return new AuditEventResponse(
                UUID.randomUUID(),
                seq,
                occurredAt,
                "secret-issuer",
                "secret-subject",
                "CREATED",
                "SUCCESS",
                PROPOSAL_KEY,
                1,
                "operations-hash",
                "assessment-hash",
                "secret reason",
                "secret-correlation",
                "prev-hash",
                "event-hash");
    }
}
