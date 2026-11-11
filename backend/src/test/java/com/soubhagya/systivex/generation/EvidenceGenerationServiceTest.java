package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soubhagya.systivex.evidence.EvidenceCategory;
import com.soubhagya.systivex.evidence.EvidenceItem;
import com.soubhagya.systivex.evidence.EvidenceMode;
import com.soubhagya.systivex.evidence.EvidencePacket;
import com.soubhagya.systivex.evidence.EvidencePacket.EvidencePacketLimits;
import com.soubhagya.systivex.evidence.EvidenceQualification;
import com.soubhagya.systivex.evidence.EvidenceReference;
import com.soubhagya.systivex.evidence.EvidenceSourceStatus;
import com.soubhagya.systivex.localmodel.Deadline;
import com.soubhagya.systivex.localmodel.LocalModelAdapter;
import com.soubhagya.systivex.localmodel.MonotonicClock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvidenceGenerationServiceTest {

    private static EvidencePacket packet(String content, List<String> limitations) {
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
                        content,
                        limitations,
                        EvidenceQualification.CURRENT,
                        EvidenceSourceStatus.AVAILABLE,
                        1,
                        0)),
                EvidencePacketLimits.defaults(),
                List.of());
    }

    private static String validResponse() {
        return "{\"claims\":[{\"statement\":\"The evidence describes the recorded state.\","
                + "\"category\":\"OBSERVED_FACT\",\"evidenceReferences\":[\"E1\"],"
                + "\"qualification\":\"CURRENT\",\"limitations\":[\"limitation-1\"]}]}";
    }

    @Test
    void validClaimIsEvaluatedButNotAccepted() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(validResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().get(0).validationResult().valid()).isTrue();
        assertThat(result.claimsValid()).isEqualTo(1);
        assertThat(result.claimsInvalid()).isZero();
    }

    @Test
    void invalidReferenceIsReportedWithoutAutoAcceptance() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(
                "{\"claims\":[{\"statement\":\"The evidence describes the recorded state.\","
                        + "\"category\":\"OBSERVED_FACT\",\"evidenceReferences\":[\"E999\"],"
                        + "\"qualification\":\"CURRENT\",\"limitations\":[]}]}");
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        GenerationResult result = service.generate(packet("content-1", List.of()));

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.claims().get(0).validationResult().valid()).isFalse();
        assertThat(result.claimsValid()).isZero();
        assertThat(result.claimsInvalid()).isEqualTo(1);
    }

    @Test
    void emptyResponseReturnsNoOutput() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn("   ");
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        GenerationResult result = service.generate(packet("content-1", List.of()));

        assertThat(result.status()).isEqualTo(GenerationStatus.NO_OUTPUT);
        assertThat(result.claims()).isEmpty();
        verify(adapter).infer(anyString());
    }

    @Test
    void oversizedPromptNeverInvokesInference() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        GenerationResult result = service.generate(packet("x".repeat(40000), List.of()));

        assertThat(result.status()).isEqualTo(GenerationStatus.PROMPT_TOO_LARGE);
        assertThat(result.claims()).isEmpty();
        verify(adapter, never()).infer(anyString());
    }

    @Test
    void nullPacketIsInvalid() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        GenerationResult result = service.generate(null);

        assertThat(result.status()).isEqualTo(GenerationStatus.INVALID_PACKET);
        assertThat(result.claims()).isEmpty();
        verify(adapter, never()).infer(anyString());
    }

    @Test
    void providerUnavailableMapsSafely() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenThrow(
                new LocalModelAdapter.AnalysisProviderUnavailableException("unavailable", new RuntimeException()));
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.PROVIDER_UNAVAILABLE);
    }

    @Test
    void busyMapsSafely() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenThrow(new LocalModelAdapter.AnalysisBusyException("busy"));
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.BUSY);
    }

    @Test
    void responseTooLargeMapsSafely() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenThrow(
                new LocalModelAdapter.AnalysisResponseTooLargeException("too large", new RuntimeException()));
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.RESPONSE_TOO_LARGE);
    }

    @Test
    void deadlineExceededFromAdapterMapsSafely() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenThrow(
                new LocalModelAdapter.AnalysisDeadlineExceededException("deadline", new RuntimeException()));
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
    }

    @Test
    void malformedParserResponseMapsToParseFailed() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn("{\"claims\":[");
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.PARSE_FAILED);
    }

    @Test
    void moreThanTwentyFiveClaimsRejectsWholeResponse() {
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        StringBuilder response = new StringBuilder("{\"claims\":[");
        for (int i = 0; i < 26; i++) {
            if (i > 0) {
                response.append(",");
            }
            response.append("{\"statement\":\"statement-").append(i)
                    .append("\",\"category\":\"OBSERVED_FACT\",")
                    .append("\"evidenceReferences\":[\"E1\"],")
                    .append("\"qualification\":\"CURRENT\",\"limitations\":[]}");
        }
        response.append("]}");
        when(adapter.infer(anyString())).thenReturn(response.toString());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper());

        assertThat(service.generate(packet("content-1", List.of())).status())
                .isEqualTo(GenerationStatus.PARSE_FAILED);
    }

    private static MonotonicClock clockThatExpiresAfter(int calls) {
        AtomicLong counter = new AtomicLong();
        return () -> counter.incrementAndGet() <= calls ? 0L : Long.MAX_VALUE / 2;
    }

    private static String twoClaimResponse() {
        return "{\"claims\":[{\"statement\":\"The evidence describes the recorded state.\","
                + "\"category\":\"OBSERVED_FACT\",\"evidenceReferences\":[\"E1\"],"
                + "\"qualification\":\"CURRENT\",\"limitations\":[\"limitation-1\"]},"
                + "{\"statement\":\"The evidence describes the recorded state.\","
                + "\"category\":\"OBSERVED_FACT\",\"evidenceReferences\":[\"E1\"],"
                + "\"qualification\":\"CURRENT\",\"limitations\":[\"limitation-1\"]}]}";
    }

    @Test
    void deadlineExpiryDuringPromptConstructionDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(2);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(validResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter, never()).infer(anyString());
    }

    @Test
    void deadlineExpiryBeforeInferenceDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(6);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(validResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter, never()).infer(anyString());
    }

    @Test
    void deadlineExpiryImmediatelyAfterInferenceDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(7);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(validResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter).infer(anyString());
    }

    @Test
    void deadlineExpiryDuringParsingDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(9);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(twoClaimResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter).infer(anyString());
    }

    @Test
    void deadlineExpiryDuringValidationDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(14);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(twoClaimResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter).infer(anyString());
    }

    @Test
    void deadlineExpiryBeforeReturnDiscardsResult() {
        MonotonicClock clock = clockThatExpiresAfter(19);
        LocalModelAdapter adapter = mock(LocalModelAdapter.class);
        when(adapter.infer(anyString())).thenReturn(twoClaimResponse());
        EvidenceGenerationService service =
                new EvidenceGenerationService(adapter, new ObjectMapper(), clock);

        GenerationResult result = service.generate(packet("content-1", List.of("limitation-1")));

        assertThat(result.status()).isEqualTo(GenerationStatus.DEADLINE_EXCEEDED);
        assertThat(result.claims()).isEmpty();
        verify(adapter).infer(anyString());
    }
}
