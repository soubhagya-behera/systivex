package com.soubhagya.systivex.generation;

import com.soubhagya.systivex.evidence.ClaimValidationResult;
import com.soubhagya.systivex.evidence.ClaimValidator;
import com.soubhagya.systivex.evidence.EvidenceClaim;
import com.soubhagya.systivex.evidence.EvidencePacket;
import com.soubhagya.systivex.localmodel.Deadline;
import com.soubhagya.systivex.localmodel.LocalModelAdapter;
import com.soubhagya.systivex.localmodel.MonotonicClock;
import com.soubhagya.systivex.localmodel.OperationDeadlineExceededException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnBean(LocalModelAdapter.class)
public final class EvidenceGenerationService {

    public static final int MAX_GENERATED_CLAIMS = StrictClaimResponseParser.SLICE3_GENERATED_CLAIM_CAP;
    public static final int MAX_PROMPT_BYTES = 32_768;

    private static final Duration TOTAL_DEADLINE = Duration.ofSeconds(45);
    private static final Duration PROMPT_DEADLINE = Duration.ofSeconds(5);
    private static final Duration PARSE_VALIDATION_DEADLINE = Duration.ofSeconds(5);
    private final LocalModelAdapter adapter;
    private final ObjectMapper objectMapper;
    private final EvidenceGenerationPromptBuilder promptBuilder;
    private final StrictClaimResponseParser parser;

    private final MonotonicClock clock;

    @Autowired
    public EvidenceGenerationService(LocalModelAdapter adapter, ObjectMapper objectMapper) {
        this(adapter, objectMapper, MonotonicClock.system());
    }

    public EvidenceGenerationService(LocalModelAdapter adapter, ObjectMapper objectMapper, MonotonicClock clock) {
        this.adapter = adapter;
        this.objectMapper = objectMapper;
        this.promptBuilder = new EvidenceGenerationPromptBuilder(objectMapper);
        this.parser = new StrictClaimResponseParser(objectMapper);
        this.clock = clock;
    }

    public GenerationResult generate(EvidencePacket packet) {
        if (packet == null) {
            return new GenerationResult(null, null, GenerationStatus.INVALID_PACKET,
                    List.of(), 0, 0, 0);
        }
        Deadline deadline = Deadline.after(TOTAL_DEADLINE, clock);
        Deadline promptDeadline = Deadline.after(PROMPT_DEADLINE, clock);
        String prompt;
        try {
            if (deadline.isExpired() || promptDeadline.isExpired()) {
                throw new com.soubhagya.systivex.localmodel.OperationDeadlineExceededException(
                        "Local model operation exceeded the overall deadline");
            }
            prompt = promptBuilder.build(packet, promptDeadline);
        } catch (OperationDeadlineExceededException ex) {
            return deadlineExceeded(packet);
        } catch (RuntimeException ex) {
            return invalidPacket(packet);
        }
        int promptBytes = EvidenceGenerationPromptBuilder.utf8ByteLength(prompt);
        if (promptBytes > MAX_PROMPT_BYTES) {
            return promptTooLarge(packet);
        }
        if (deadline.isExpired()) {
            return deadlineExceeded(packet);
        }

        String response;
        try {
            response = adapter.infer(prompt);
        } catch (LocalModelAdapter.AnalysisBusyException ex) {
            return busy(packet);
        } catch (LocalModelAdapter.AnalysisProviderUnavailableException ex) {
            return providerUnavailable(packet);
        } catch (LocalModelAdapter.AnalysisResponseTooLargeException ex) {
            return responseTooLarge(packet);
        } catch (LocalModelAdapter.AnalysisDeadlineExceededException ex) {
            return deadlineExceeded(packet);
        } catch (LocalModelAdapter.ModelIdentityMismatchException ex) {
            return providerUnavailable(packet);
        } catch (OperationDeadlineExceededException ex) {
            return deadlineExceeded(packet);
        } catch (RuntimeException ex) {
            return providerUnavailable(packet);
        }
        if (deadline.isExpired()) {
            return deadlineExceeded(packet);
        }
        if (response == null || response.isBlank()) {
            return noOutput(packet);
        }

        java.time.Duration parseAllowance = deadline.remaining().compareTo(PARSE_VALIDATION_DEADLINE) < 0
                ? deadline.remaining()
                : PARSE_VALIDATION_DEADLINE;
        Deadline parseDeadline = Deadline.after(parseAllowance, clock);
        List<StrictClaimResponseParser.ParsedClaim> parsed;
        try {
            parsed = parser.parse(response, parseDeadline);
        } catch (OperationDeadlineExceededException ex) {
            return deadlineExceeded(packet);
        } catch (RuntimeException ex) {
            return parseFailed(packet);
        }
        if (deadline.isExpired() || parseDeadline.isExpired()) {
            return deadlineExceeded(packet);
        }

        List<GeneratedClaimResult> candidates = new ArrayList<>();
        int claimsValid = 0;
        int claimsInvalid = 0;
        try {
            for (StrictClaimResponseParser.ParsedClaim raw : parsed) {
                if (deadline.isExpired() || parseDeadline.isExpired()) {
                    return deadlineExceeded(packet);
                }
                List<com.soubhagya.systivex.evidence.EvidenceReference> references =
                        new ArrayList<>();
                for (String value : raw.evidenceReferences()) {
                    references.add(new com.soubhagya.systivex.evidence.EvidenceReference(value));
                }
                com.soubhagya.systivex.evidence.EvidenceClaim claim =
                        new com.soubhagya.systivex.evidence.EvidenceClaim(
                                raw.statement(),
                                raw.category(),
                                references,
                                raw.qualification(),
                                raw.limitations(),
                                packet.packetRef(),
                                packet.mode());
                com.soubhagya.systivex.evidence.ClaimValidationResult result =
                        com.soubhagya.systivex.evidence.ClaimValidator.validate(packet, claim);
                candidates.add(new GeneratedClaimResult(claim, result));
                if (result.valid()) {
                    claimsValid++;
                } else {
                    claimsInvalid++;
                }
            }
        } catch (RuntimeException ex) {
            if (deadline.isExpired() || ex instanceof OperationDeadlineExceededException) {
                return deadlineExceeded(packet);
            }
            return parseFailed(packet);
        }
        if (deadline.isExpired() || parseDeadline.isExpired()) {
            return deadlineExceeded(packet);
        }
        return new GenerationResult(
                packet.packetRef(),
                packet.mode(),
                GenerationStatus.SUCCEEDED,
                candidates,
                parsed.size(),
                claimsValid,
                claimsInvalid);
    }

    private GenerationResult deadlineExceeded(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.DEADLINE_EXCEEDED, List.of(), 0, 0, 0);
    }

    private GenerationResult invalidPacket(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.INVALID_PACKET, List.of(), 0, 0, 0);
    }

    private GenerationResult promptTooLarge(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.PROMPT_TOO_LARGE, List.of(), 0, 0, 0);
    }

    private GenerationResult busy(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.BUSY, List.of(), 0, 0, 0);
    }

    private GenerationResult providerUnavailable(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.PROVIDER_UNAVAILABLE, List.of(), 0, 0, 0);
    }

    private GenerationResult responseTooLarge(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.RESPONSE_TOO_LARGE, List.of(), 0, 0, 0);
    }

    private GenerationResult noOutput(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.NO_OUTPUT, List.of(), 0, 0, 0);
    }

    private GenerationResult parseFailed(com.soubhagya.systivex.evidence.EvidencePacket packet) {
        return new GenerationResult(packet.packetRef(), packet.mode(),
                GenerationStatus.PARSE_FAILED, List.of(), 0, 0, 0);
    }
}
