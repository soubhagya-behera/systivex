package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.evidence.EvidenceCategory;
import com.soubhagya.systivex.evidence.EvidenceItem;
import com.soubhagya.systivex.evidence.EvidenceMode;
import com.soubhagya.systivex.evidence.EvidenceOmission;
import com.soubhagya.systivex.evidence.EvidencePacket;
import com.soubhagya.systivex.evidence.EvidencePacket.EvidencePacketLimits;
import com.soubhagya.systivex.evidence.EvidenceQualification;
import com.soubhagya.systivex.evidence.EvidenceReference;
import com.soubhagya.systivex.evidence.EvidenceSourceStatus;
import com.soubhagya.systivex.localmodel.Deadline;
import com.soubhagya.systivex.localmodel.MonotonicClock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvidenceGenerationPromptBuilderTest {

    private static EvidencePacket packet() {
        return new EvidencePacket(
                EvidenceReference.packet(1),
                EvidenceMode.BASELINE,
                List.of(new EvidenceItem<>(
                        EvidenceReference.of(1),
                        EvidenceCategory.OBSERVED_FACT,
                        "twin.entity",
                        "scope-1",
                        null,
                        List.of(),
                        "content-1",
                        List.of("limitation-1"),
                        EvidenceQualification.CURRENT,
                        EvidenceSourceStatus.AVAILABLE,
                        1,
                        0)),
                EvidencePacketLimits.defaults(),
                List.of(new EvidenceOmission(
                        "change.simulation",
                        "Simulation evidence is not applicable to BASELINE mode.",
                        EvidenceSourceStatus.OMITTED)));
    }

    @Test
    void buildIsDeterministicAndContainsPacketReferences() {
        EvidenceGenerationPromptBuilder builder =
                new EvidenceGenerationPromptBuilder(new ObjectMapper());
        Deadline deadline = Deadline.after(Duration.ofSeconds(5), MonotonicClock.system());

        String first = builder.build(packet(), deadline);
        String second = builder.build(packet(), deadline);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains("UNTRUSTED EVIDENCE DATA");
        assertThat(first).contains("\"E1\"");
        assertThat(first).contains("limitation-1");
    }

    @Test
    void utf8ByteLengthCountsMultibyteContent() {
        assertThat(EvidenceGenerationPromptBuilder.utf8ByteLength("a\u00e9")).isEqualTo(3);
    }

    @Test
    void expiredDeadlineRejectsPromptConstruction() {
        EvidenceGenerationPromptBuilder builder =
                new EvidenceGenerationPromptBuilder(new ObjectMapper());
        Deadline deadline = Deadline.after(Duration.ZERO, MonotonicClock.system());

        assertThatThrownBy(() -> builder.build(packet(), deadline))
                .isInstanceOf(com.soubhagya.systivex.localmodel.OperationDeadlineExceededException.class);
    }
}
