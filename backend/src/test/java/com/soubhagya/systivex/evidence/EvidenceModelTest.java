package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class EvidenceModelTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void evidenceCategoriesAreExactlyFive() {
        assertThat(EvidenceCategory.values()).hasSize(5);
    }

    @Test
    void sourceStatusesAreExactlyFour() {
        assertThat(EvidenceSourceStatus.values()).hasSize(4);
    }

    @Test
    void packetModesAreMutuallyExclusiveValues() {
        assertThat(EvidenceMode.values())
                .containsExactly(
                        EvidenceMode.BASELINE,
                        EvidenceMode.SIMULATION,
                        EvidenceMode.POLICY_EVALUATION);
    }

    @Test
    void evidenceReferenceAcceptsOnlyCanonicalForms() {
        assertThat(EvidenceReference.of(1).value()).isEqualTo("E1");
        assertThat(EvidenceReference.of(42).value()).isEqualTo("E42");
        assertThat(EvidenceReference.packet(1).value()).isEqualTo("P1");
        assertThatThrownBy(() -> new EvidenceReference(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("E0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("E01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("e1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceReference("X1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvidenceReference.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvidenceReference.packet(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceItemRequiresReferenceQualificationAndStatus() {
        assertThatThrownBy(() -> item(null, EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(EvidenceReference.of(1), null, EvidenceSourceStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(EvidenceReference.of(1), EvidenceQualification.CURRENT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evidenceItemCopiesListsDefensively() {
        List<String> limitations = new ArrayList<>();
        limitations.add("limitation");
        EvidenceItem<String> item = new EvidenceItem<>(
                EvidenceReference.of(1),
                EvidenceCategory.OBSERVED_FACT,
                "source",
                "scope",
                null,
                List.of(),
                "content",
                limitations,
                EvidenceQualification.CURRENT,
                EvidenceSourceStatus.AVAILABLE,
                1,
                0);
        limitations.add("mutated");
        assertThat(item.limitations()).containsExactly("limitation");
        assertThatThrownBy(() -> item.limitations().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void evidenceItemWithRefAndTruncationPreserveOtherFields() {
        EvidenceItem<String> item = item(
                EvidenceReference.of(1), EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE);
        EvidenceItem<String> renumbered = item.withRef(EvidenceReference.of(7));
        assertThat(renumbered.ref().value()).isEqualTo("E7");
        assertThat(renumbered.content()).isEqualTo("content");

        EvidenceItem<String> truncated = renumbered.withTruncation(
                EvidenceSourceStatus.TRUNCATED,
                EvidenceQualification.TRUNCATED,
                List.of("capped"),
                9,
                4);
        assertThat(truncated.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(truncated.qualification()).isEqualTo(EvidenceQualification.TRUNCATED);
        assertThat(truncated.limitations()).containsExactly("capped");
        assertThat(truncated.includedCount()).isEqualTo(9);
        assertThat(truncated.omittedCount()).isEqualTo(4);
    }

    @Test
    void evidenceLimitsTruncateAtDocumentedBoundaries() {
        assertThat(EvidenceLimits.first(List.of("a", "b", "c"), 2))
                .containsExactly("a", "b");

        String exactly = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH);
        String tooLong = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        assertThat(EvidenceLimits.text(exactly)).isEqualTo(exactly);
        assertThat(EvidenceLimits.text(tooLong)).startsWith("x".repeat(100));
        assertThat(EvidenceLimits.text(tooLong)).endsWith("[truncated]");
        assertThat(EvidenceLimits.exceeds(tooLong, EvidenceLimits.MAX_TEXT_LENGTH)).isTrue();
        assertThat(EvidenceLimits.exceeds(exactly, EvidenceLimits.MAX_TEXT_LENGTH)).isFalse();

        String deltaExactly = "y".repeat(EvidenceLimits.MAX_DELTA_STRING_LENGTH);
        String deltaTooLong = "y".repeat(EvidenceLimits.MAX_DELTA_STRING_LENGTH + 1);
        assertThat(EvidenceLimits.text(deltaExactly, EvidenceLimits.MAX_DELTA_STRING_LENGTH))
                .isEqualTo(deltaExactly);
        assertThat(EvidenceLimits.text(deltaTooLong, EvidenceLimits.MAX_DELTA_STRING_LENGTH))
                .endsWith("[truncated]");
    }

    @Test
    void boundedTextReportsEntryAndTextLimits() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            values.add("value-" + i);
        }
        EvidenceLimits.BoundedText entryBounded = EvidenceLimits.textList(values, 2, 100);
        assertThat(entryBounded.values()).hasSize(2);
        assertThat(entryBounded.truncated()).isTrue();

        EvidenceLimits.BoundedText textBounded = EvidenceLimits.textList(
                List.of("z".repeat(101)), 10, 100);
        assertThat(textBounded.values().get(0)).endsWith("[truncated]");
        assertThat(textBounded.truncated()).isTrue();
    }

    @Test
    void packetLimitsExposeNestedCapsAndDefaults() {
        EvidencePacket.EvidencePacketLimits defaults =
                EvidencePacket.EvidencePacketLimits.defaults();
        assertThat(defaults.maxTwinEntities()).isEqualTo(EvidenceLimits.MAX_TWIN_ENTITIES);
        assertThat(defaults.maxTwinRelationships()).isEqualTo(EvidenceLimits.MAX_TWIN_RELATIONSHIPS);
        assertThat(defaults.maxImpactedEntities()).isEqualTo(EvidenceLimits.MAX_IMPACTED_ENTITIES);
        assertThat(defaults.maxImpactPathsPacket()).isEqualTo(EvidenceLimits.MAX_IMPACT_PATHS_PACKET);
        assertThat(defaults.maxRiskEntries()).isEqualTo(EvidenceLimits.MAX_RISK_ENTRIES);
        assertThat(defaults.maxPolicyRules()).isEqualTo(EvidenceLimits.MAX_POLICY_RULES);
        assertThat(defaults.maxDeltaStrings()).isEqualTo(EvidenceLimits.MAX_DELTA_STRINGS);
        assertThat(defaults.maxDeltaStringLength()).isEqualTo(EvidenceLimits.MAX_DELTA_STRING_LENGTH);
        assertThat(defaults.maxRiskReasons()).isEqualTo(EvidenceLimits.MAX_RISK_REASONS);
        assertThat(defaults.maxTextLength()).isEqualTo(EvidenceLimits.MAX_TEXT_LENGTH);
        assertThat(defaults.maxLimitations()).isEqualTo(EvidenceLimits.MAX_LIMITATIONS);
        assertThat(defaults.maxProposalOperations()).isEqualTo(EvidenceLimits.MAX_PROPOSAL_OPERATIONS);
    }

    @Test
    void evidencePacketCopiesItemsAndOmissionsAndRequiresUniqueRefs() {
        EvidenceItem<String> first = item(
                EvidenceReference.of(1), EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE);
        List<EvidenceItem<?>> items = new ArrayList<>();
        items.add(first);
        EvidencePacket packet = new EvidencePacket(
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE,
                items,
                EvidencePacket.EvidencePacketLimits.defaults(),
                List.of(new EvidenceOmission("twin", "not requested", EvidenceSourceStatus.OMITTED)));
        items.clear();
        assertThat(packet.items()).hasSize(1);
        assertThat(packet.omissions()).hasSize(1);
        assertThatThrownBy(() -> packet.items().add(first))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1),
                        EvidenceMode.BASELINE,
                        List.of(first, first),
                        EvidencePacket.EvidencePacketLimits.defaults(),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void evidencePacketRejectsWrongReferenceForms() {
        EvidenceItem<String> item = item(
                EvidenceReference.of(1), EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE);
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.of(1),
                        EvidenceMode.BASELINE,
                        List.of(item),
                        EvidencePacket.EvidencePacketLimits.defaults(),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P<n>");

        EvidenceItem<String> packetRefItem = item(
                EvidenceReference.packet(1),
                EvidenceQualification.CURRENT,
                EvidenceSourceStatus.AVAILABLE);
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1),
                        EvidenceMode.BASELINE,
                        List.of(packetRefItem),
                        EvidencePacket.EvidencePacketLimits.defaults(),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E<n>");
    }

    @Test
    void evidencePacketEnforcesTotalAndPerSourceLimits() {
        EvidenceItem<String> first = item(
                EvidenceReference.of(1), EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE);
        EvidenceItem<String> second = item(
                EvidenceReference.of(2), EvidenceQualification.CURRENT, EvidenceSourceStatus.AVAILABLE);

        EvidencePacket.EvidencePacketLimits tooSmallTotal = limits(10, 1);
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1),
                        EvidenceMode.BASELINE,
                        List.of(first, second),
                        tooSmallTotal,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total item limit");

        EvidencePacket.EvidencePacketLimits tooSmallFamily = limits(1, 10);
        assertThatThrownBy(() -> new EvidencePacket(
                        EvidenceReference.packet(1),
                        EvidenceMode.BASELINE,
                        List.of(first, second),
                        tooSmallFamily,
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source family");
    }

    @Test
    void proposalEvidenceDeepCopiesJson() {
        ObjectNode operations = MAPPER.createObjectNode();
        ArrayNode removed = operations.putArray("removedEntities");
        removed.add("entity-a");
        ObjectNode assessment = MAPPER.createObjectNode();
        assessment.put("score", 7);

        ProposalEvidence evidence = new ProposalEvidence(
                UUID.randomUUID(),
                1,
                "DRAFT",
                operations,
                1,
                false,
                1,
                0,
                "operations-hash",
                assessment,
                "assessment-hash",
                "ALLOW",
                "policy-v1",
                "risk-v1",
                "fingerprint",
                null,
                null,
                null,
                null);

        removed.add("entity-b");
        assessment.put("score", 9);
        assertThat(evidence.operations().get("removedEntities")).hasSize(1);
        assertThat(evidence.assessment().get("score").asInt()).isEqualTo(7);

        ((ArrayNode) evidence.operations().get("removedEntities")).add("entity-c");
        assertThat(evidence.operations().get("removedEntities")).hasSize(1);
    }

    @Test
    void runtimeObservationCountersAreSortedAndImmutable() {
        Map<String, Long> counters = new java.util.HashMap<>();
        counters.put("z.counter", 1L);
        counters.put("a.counter", 2L);
        RuntimeObservationEvidence evidence = new RuntimeObservationEvidence(
                "svc", true, "UP", null, counters, List.of("limitation"));
        counters.put("m.counter", 3L);

        assertThat(evidence.counters().keySet())
                .containsExactly("a.counter", "z.counter");
        assertThatThrownBy(() -> evidence.counters().put("x", 4L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void riskEvidenceCopiesNestedLists() {
        RiskEvidence.RiskEntryEvidence entry = new RiskEvidence.RiskEntryEvidence(
                UUID.randomUUID(),
                "SERVICE",
                "name",
                "ref",
                1,
                "start",
                com.soubhagya.systivex.intelligence.RiskLevel.HIGH,
                List.of("reason"),
                false,
                false,
                false,
                0);
        List<RiskEvidence.RiskEntryEvidence> risks = new ArrayList<>();
        risks.add(entry);
        RiskEvidence evidence = new RiskEvidence(
                List.of(), risks, 3, "risk-v1", null, 1, 1, false, "note");
        risks.clear();
        assertThat(evidence.risks()).hasSize(1);
        assertThatThrownBy(() -> evidence.risks().add(entry))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> entry.reasons().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void auditEvidenceRequiresNonNegativeCounts() {
        AuditEvidence evidence = new AuditEvidence(List.of(), true, 100, 3);
        assertThat(evidence.omittedCount()).isEqualTo(3);
        assertThatThrownBy(() -> new AuditEvidence(List.of(), true, 100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EvidenceItem<String> item(
            EvidenceReference ref,
            EvidenceQualification qualification,
            EvidenceSourceStatus status) {
        return new EvidenceItem<>(
                ref,
                EvidenceCategory.OBSERVED_FACT,
                "source",
                "scope",
                null,
                List.of(),
                "content",
                List.of(),
                qualification,
                status,
                1,
                0);
    }

    private static EvidencePacket.EvidencePacketLimits limits(
            int maxPerSourceFamily, int maxTotalItems) {
        return new EvidencePacket.EvidencePacketLimits(
                10,
                1,
                10,
                6,
                500,
                2000,
                maxPerSourceFamily,
                maxTotalItems,
                262_144,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                512,
                20,
                1000,
                100,
                100);
    }
}
