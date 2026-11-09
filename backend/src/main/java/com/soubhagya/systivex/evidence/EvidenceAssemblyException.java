package com.soubhagya.systivex.evidence;

public class EvidenceAssemblyException extends RuntimeException {

    public static final String PACKET_TOO_LARGE = "PACKET_TOO_LARGE";
    public static final String SERIALIZATION_FAILED = "SERIALIZATION_FAILED";

    private final String code;

    public EvidenceAssemblyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EvidenceAssemblyException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
