package com.soubhagya.systivex.generation;

public enum GenerationStatus {
    SUCCEEDED,
    NO_OUTPUT,
    PARSE_FAILED,
    PROVIDER_UNAVAILABLE,
    DEADLINE_EXCEEDED,
    RESPONSE_TOO_LARGE,
    BUSY,
    PROMPT_TOO_LARGE,
    INVALID_PACKET
}
