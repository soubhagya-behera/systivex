package com.soubhagya.systivex.generation;

import com.soubhagya.systivex.evidence.EvidenceCategory;
import com.soubhagya.systivex.evidence.EvidenceQualification;
import com.soubhagya.systivex.localmodel.Deadline;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class StrictClaimResponseParser {

    public static final int D40_STRUCTURAL_CLAIM_LIMIT = 100;
    public static final int SLICE3_GENERATED_CLAIM_CAP = 25;

    private final ObjectMapper strictMapper;

    public StrictClaimResponseParser(ObjectMapper objectMapper) {
        // Jackson counts maxDocumentLength in characters for this textual JSON parser.
        // LocalModelTransport.MAX_RESPONSE_BYTES remains the outer 65,536-byte
        // response-body bound. The parser limit and transport limit serve different
        // purposes, and neither is a token-count limit.
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(8)
                .maxDocumentLength(65_536)
                .maxStringLength(1000)
                .maxNameLength(64)
                .build();
        tools.jackson.core.json.JsonFactory factory = tools.jackson.core.json.JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();
        this.strictMapper = tools.jackson.databind.json.JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public List<ParsedClaim> parse(String response, Deadline deadline) {
        if (response == null || response.isBlank()) {
            throw new MalformedResponseException("response must not be blank");
        }
        if (deadline != null && deadline.isExpired()) {
            throw new com.soubhagya.systivex.localmodel.OperationDeadlineExceededException("Local model operation exceeded the overall deadline");
        }
        List<ParsedClaim> claims = new ArrayList<>();
        try (JsonParser parser = strictMapper.createParser(response)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new MalformedResponseException("response must be a JSON object");
            }
            String firstField = parser.nextName();
            if (!"claims".equals(firstField)) {
                throw new MalformedResponseException("response must contain a claims field");
            }
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new MalformedResponseException("claims must be an array");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (deadline != null && deadline.isExpired()) {
                    throw new com.soubhagya.systivex.localmodel.OperationDeadlineExceededException("Local model operation exceeded the overall deadline");
                }
                if (parser.currentToken() == JsonToken.VALUE_NULL) {
                    throw new MalformedResponseException("claims must not contain null elements");
                }
                RawClaim raw = strictMapper.readerFor(RawClaim.class)
                        .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .readValue(parser);
                claims.add(new ParsedClaim(
                        raw.statement(),
                        raw.category(),
                        raw.evidenceReferences(),
                        raw.qualification(),
                        raw.limitations()));
                if (claims.size() > D40_STRUCTURAL_CLAIM_LIMIT) {
                    throw new StructuralLimitExceededException(
                            "response exceeds the 100-claim structural parser limit");
                }
            }
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                throw new MalformedResponseException("response must not contain trailing tokens");
            }
            if (parser.nextToken() != null) {
                throw new MalformedResponseException("response must not contain trailing tokens");
            }
            if (claims.size() > SLICE3_GENERATED_CLAIM_CAP) {
                throw new GenerationClaimCapExceededException(
                        "response exceeds the 25-claim generation cap");
            }
            return List.copyOf(claims);
        } catch (JacksonException ex) {
            throw new MalformedResponseException("response is not valid JSON: " + ex.getMessage(), ex);
        }    }

    public record ParsedClaim(
            String statement,
            EvidenceCategory category,
            List<String> evidenceReferences,
            EvidenceQualification qualification,
            List<String> limitations) {
        public ParsedClaim {
            if (evidenceReferences == null) {
                evidenceReferences = List.of();
            }
            if (limitations == null) {
                limitations = List.of();
            }
            evidenceReferences = List.copyOf(evidenceReferences);
            limitations = List.copyOf(limitations);
        }
    }

    private record RawClaim(
            String statement,
            EvidenceCategory category,
            List<String> evidenceReferences,
            EvidenceQualification qualification,
            List<String> limitations) {
        public RawClaim {
            if (evidenceReferences == null) {
                evidenceReferences = List.of();
            }
            if (limitations == null) {
                limitations = List.of();
            }
        }
    }

    public static final class MalformedResponseException extends RuntimeException {
        public MalformedResponseException(String message) {
            super(message);
        }

        public MalformedResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class StructuralLimitExceededException extends RuntimeException {
        public StructuralLimitExceededException(String message) {
            super(message);
        }
    }

    public static final class GenerationClaimCapExceededException extends RuntimeException {
        public GenerationClaimCapExceededException(String message) {
            super(message);
        }
    }

}
