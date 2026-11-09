package com.soubhagya.systivex.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.intelligence.ImpactDirection;
import com.soubhagya.systivex.intelligence.ImpactResponse;
import com.soubhagya.systivex.intelligence.RiskLevel;
import com.soubhagya.systivex.intelligence.RiskResponse;
import com.soubhagya.systivex.twin.model.SystemEntityType;
import com.soubhagya.systivex.twin.model.SystemRelationshipType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntelligenceEvidenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void impactAdapterPreservesCompletePathsAndSortsCollections() {
        ImpactResponse.StartEntity startSource = start("z", SystemEntityType.SERVICE);
        ImpactResponse.StartEntity startOther = start("a", SystemEntityType.API);
        ImpactResponse.ImpactStep firstStep = step("source-z", "target-z");
        ImpactResponse.ImpactStep secondStep = step("target-z", "leaf-z");
        ImpactResponse.ImpactPath pathZ = path("z", 2, List.of(firstStep, secondStep));
        ImpactResponse.ImpactPath pathA = path("a", 1, List.of(step("source-a", "target-a")));
        ImpactResponse.ImpactedEntity impactedZ = impacted("z", 2);
        ImpactResponse.ImpactedEntity impactedA = impacted("a", 1);
        ImpactResponse impact = impact(
                List.of(startSource, startOther),
                List.of(impactedZ, impactedA),
                List.of(pathZ, pathA),
                false,
                null);

        EvidenceItem<ImpactEvidence> item = ImpactEvidenceAdapter.adaptItem(impact);

        assertThat(item.category()).isEqualTo(EvidenceCategory.DETERMINISTIC_FINDING);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
        ImpactEvidence content = item.content();
        assertThat(content.startedFrom()).extracting(ImpactEvidence.ImpactStartEvidence::externalRef)
                .containsExactly("a", "z");
        assertThat(content.impacted()).extracting(ImpactEvidence.ImpactedEntityEvidence::externalRef)
                .containsExactly("a", "z");
        assertThat(content.paths()).extracting(ImpactEvidence.ImpactPathEvidence::targetExternalRef)
                .containsExactly("a", "z");
        ImpactEvidence.ImpactPathEvidence sortedZ = content.paths().get(1);
        assertThat(sortedZ.steps()).extracting(ImpactEvidence.ImpactStepEvidence::sourceExternalRef)
                .containsExactly("source-z", "target-z");
        assertThat(sortedZ.steps()).extracting(ImpactEvidence.ImpactStepEvidence::targetExternalRef)
                .containsExactly("target-z", "leaf-z");
    }

    @Test
    void impactAdapterCapsImpactedEntitiesAt100() {
        List<ImpactResponse.ImpactedEntity> impacted = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            impacted.add(impacted(String.format("e%03d", i), 1));
        }
        ImpactResponse impact = impact(List.of(), impacted, List.of(), false, null);

        EvidenceItem<ImpactEvidence> item = ImpactEvidenceAdapter.adaptItem(impact);

        assertThat(item.content().impacted()).hasSize(100);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.limitations()).anyMatch(value -> value.contains("capped"));
    }

    @Test
    void impactAdapterCapsStartsAndPaths() {
        List<ImpactResponse.StartEntity> starts = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            starts.add(start(String.format("s%03d", i), SystemEntityType.SERVICE));
        }
        List<ImpactResponse.ImpactPath> paths = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            paths.add(path(String.format("p%03d", i), 1, List.of(step("s", "t"))));
        }
        ImpactResponse impact = impact(starts, List.of(), paths, false, null);

        EvidenceItem<ImpactEvidence> item = ImpactEvidenceAdapter.adaptItem(impact);

        assertThat(item.content().startedFrom()).hasSize(10);
        assertThat(item.content().paths()).hasSize(100);
        assertThat(item.content().paths().get(0).steps()).hasSize(1);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(2);
    }

    @Test
    void impactAdapterHonoursServerTruncation() {
        ImpactResponse impact = impact(List.of(), List.of(), List.of(), true, "node cap reached");

        EvidenceItem<ImpactEvidence> item = ImpactEvidenceAdapter.adaptItem(impact);

        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.TRUNCATED);
        assertThat(item.content().truncated()).isTrue();
        assertThat(item.content().truncationReason()).isEqualTo("node cap reached");
        assertThat(item.limitations()).anyMatch(value -> value.contains("Server reported"));
    }

    @Test
    void impactAdapterFlagsTruncatedNoteWithoutInventingCollectionCaps() {
        String longNote = "n".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        ImpactResponse impact = impact(List.of(), List.of(), List.of(), false, null, longNote);

        EvidenceItem<ImpactEvidence> item = ImpactEvidenceAdapter.adaptItem(impact);

        assertThat(item.content().note()).endsWith("[truncated]");
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.TRUNCATED);
        assertThat(item.limitations()).anyMatch(
                value -> value.contains("Impact note was truncated"));
        assertThat(item.limitations()).noneMatch(
                value -> value.contains("Impact evidence capped at"));
    }

    @Test
    void riskAdapterSortsBySeverityAndReasons() {
        List<RiskResponse.RiskEntry> risks = List.of(
                risk("low", RiskLevel.LOW, List.of("b", "a"), false, false),
                risk("high", RiskLevel.HIGH, List.of("z"), false, false),
                risk("unknown", RiskLevel.UNKNOWN, List.of(), false, false),
                risk("medium", RiskLevel.MEDIUM, List.of(), false, false));
        RiskResponse risk = risk(risks, "note");

        EvidenceItem<RiskEvidence> item = RiskEvidenceAdapter.adaptItem(risk);

        assertThat(item.category()).isEqualTo(EvidenceCategory.DETERMINISTIC_FINDING);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.AVAILABLE);
        assertThat(item.qualification()).isEqualTo(EvidenceQualification.CURRENT);
        assertThat(item.content().risks()).extracting(RiskEvidence.RiskEntryEvidence::level)
                .containsExactly(RiskLevel.HIGH, RiskLevel.UNKNOWN, RiskLevel.MEDIUM, RiskLevel.LOW);
        assertThat(item.content().risks().get(3).reasons()).containsExactly("a", "b");
    }

    @Test
    void riskAdapterFlagsUncertaintyAndStaleness() {
        RiskResponse uncertain = risk(
                List.of(risk("a", RiskLevel.HIGH, List.of(), true, false)), null);
        RiskResponse stale = risk(
                List.of(risk("a", RiskLevel.LOW, List.of(), false, true)), null);

        assertThat(RiskEvidenceAdapter.adaptItem(uncertain).qualification())
                .isEqualTo(EvidenceQualification.UNCERTAIN);
        assertThat(RiskEvidenceAdapter.adaptItem(stale).qualification())
                .isEqualTo(EvidenceQualification.STALE);
    }

    @Test
    void riskAdapterCapsEntriesAt100() {
        List<RiskResponse.RiskEntry> risks = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            risks.add(risk(String.format("e%03d", i), RiskLevel.LOW, List.of(), false, false));
        }
        RiskResponse risk = risk(risks, null);

        EvidenceItem<RiskEvidence> item = RiskEvidenceAdapter.adaptItem(risk);

        assertThat(item.content().risks()).hasSize(100);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.content().truncated()).isTrue();
    }

    @Test
    void riskAdapterCapsReasonsAndFlagsEntry() {
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            reasons.add(String.format("reason-%02d", i));
        }
        RiskResponse risk = risk(
                List.of(risk("a", RiskLevel.HIGH, reasons, false, false)), null);

        EvidenceItem<RiskEvidence> item = RiskEvidenceAdapter.adaptItem(risk);

        RiskEvidence.RiskEntryEvidence entry = item.content().risks().get(0);
        assertThat(entry.reasons()).hasSize(20);
        assertThat(entry.truncated()).isTrue();
        assertThat(entry.omittedReasonCount()).isEqualTo(1);
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
        assertThat(item.omittedCount()).isEqualTo(1);
        assertThat(item.limitations()).anyMatch(value -> value.contains("Risk reasons truncated"));
    }

    @Test
    void riskAdapterTruncatesLongReasonText() {
        String longReason = "x".repeat(EvidenceLimits.MAX_TEXT_LENGTH + 1);
        RiskResponse risk = risk(
                List.of(risk("a", RiskLevel.HIGH, List.of(longReason), false, false)), null);

        EvidenceItem<RiskEvidence> item = RiskEvidenceAdapter.adaptItem(risk);

        RiskEvidence.RiskEntryEvidence entry = item.content().risks().get(0);
        assertThat(entry.reasons().get(0)).endsWith("[truncated]");
        assertThat(entry.truncated()).isTrue();
        assertThat(entry.omittedReasonCount()).isZero();
        assertThat(item.status()).isEqualTo(EvidenceSourceStatus.TRUNCATED);
    }

    private static ImpactResponse impact(
            List<ImpactResponse.StartEntity> starts,
            List<ImpactResponse.ImpactedEntity> impacted,
            List<ImpactResponse.ImpactPath> paths,
            boolean truncated,
            String truncationReason) {
        return impact(starts, impacted, paths, truncated, truncationReason, "impact note");
    }

    private static ImpactResponse impact(
            List<ImpactResponse.StartEntity> starts,
            List<ImpactResponse.ImpactedEntity> impacted,
            List<ImpactResponse.ImpactPath> paths,
            boolean truncated,
            String truncationReason,
            String note) {
        return new ImpactResponse(
                starts,
                impacted,
                paths,
                6,
                impacted.size(),
                paths.size(),
                truncated,
                truncationReason,
                false,
                NOW,
                20,
                20,
                new ImpactResponse.Limits(10, 10, 500, 2000),
                note);
    }

    private static RiskResponse risk(List<RiskResponse.RiskEntry> risks, String note) {
        return new RiskResponse(
                List.of(start("start", SystemEntityType.SERVICE)),
                risks,
                6,
                "risk-v1",
                NOW,
                20,
                20,
                note);
    }

    private static ImpactResponse.StartEntity start(String externalRef, SystemEntityType type) {
        return new ImpactResponse.StartEntity(
                UUID.randomUUID(), type, externalRef, externalRef, "prod");
    }

    private static ImpactResponse.ImpactedEntity impacted(String externalRef, int depth) {
        return new ImpactResponse.ImpactedEntity(
                UUID.randomUUID(),
                SystemEntityType.SERVICE,
                externalRef,
                externalRef,
                depth,
                "start");
    }

    private static ImpactResponse.ImpactPath path(
            String targetExternalRef, int depth, List<ImpactResponse.ImpactStep> steps) {
        return new ImpactResponse.ImpactPath(
                UUID.randomUUID(), targetExternalRef, depth, steps, "explanation");
    }

    private static ImpactResponse.ImpactStep step(String sourceRef, String targetRef) {
        return new ImpactResponse.ImpactStep(
                UUID.randomUUID(),
                sourceRef,
                UUID.randomUUID(),
                targetRef,
                SystemRelationshipType.CALLS,
                ImpactDirection.FORWARD);
    }

    private static RiskResponse.RiskEntry risk(
            String externalRef,
            RiskLevel level,
            List<String> reasons,
            boolean uncertain,
            boolean stale) {
        return new RiskResponse.RiskEntry(
                UUID.randomUUID(),
                "SERVICE",
                externalRef,
                externalRef,
                1,
                "start",
                level,
                reasons,
                uncertain,
                stale);
    }
}
