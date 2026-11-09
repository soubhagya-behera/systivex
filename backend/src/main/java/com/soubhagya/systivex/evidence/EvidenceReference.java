package com.soubhagya.systivex.evidence;

import java.util.regex.Pattern;

/**
 * Packet-local reference. References are transient, deterministic, and must
 * never be treated as durable or global evidence identifiers. Only the
 * {@code E<n>} and {@code P<n>} forms are accepted.
 */
public record EvidenceReference(String value) {

    private static final Pattern FORMAT = Pattern.compile("[EP][1-9][0-9]*");

    public EvidenceReference {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Evidence reference must match [EP]<positive integer>: " + value);
        }
    }

    public static EvidenceReference of(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("Evidence reference index must be positive");
        }
        return new EvidenceReference("E" + index);
    }

    public static EvidenceReference packet(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("Packet reference index must be positive");
        }
        return new EvidenceReference("P" + index);
    }

    @Override
    public String toString() {
        return value;
    }
}
