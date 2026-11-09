package com.soubhagya.systivex.evidence;

import java.util.ArrayList;
import java.util.List;

public final class EvidenceLimits {

    public static final int MAX_ROOTS = 10;
    public static final int MIN_DEPTH = 1;
    public static final int MAX_DEPTH = 10;
    public static final int DEFAULT_DEPTH = 6;

    public static final int MAX_IMPACT_NODES = 500;
    public static final int MAX_IMPACT_PATHS = 2000;

    public static final int MAX_ITEMS_PER_SOURCE_FAMILY = 100;
    public static final int MAX_TOTAL_ITEMS = 1000;
    public static final int MAX_SERIALIZED_BYTES = 262_144;

    public static final int MAX_TWIN_ENTITIES = 100;
    public static final int MAX_TWIN_RELATIONSHIPS = 100;
    public static final int MAX_IMPACTED_ENTITIES = 100;
    public static final int MAX_IMPACT_PATHS_PACKET = 100;
    public static final int MAX_RISK_ENTRIES = 100;
    public static final int MAX_POLICY_RULES = 100;
    public static final int MAX_AUDIT_EVENTS = 100;

    public static final int MAX_DELTA_STRINGS = 100;
    public static final int MAX_DELTA_STRING_LENGTH = 512;
    public static final int MAX_RISK_REASONS = 20;
    public static final int MAX_TEXT_LENGTH = 1000;
    public static final int MAX_LIMITATIONS = 100;
    public static final int MAX_SIMULATION_OPERATIONS = 100;
    public static final int MAX_PROPOSAL_OPERATIONS = 100;

    private static final String TRUNCATION_MARKER = "... [truncated]";

    private EvidenceLimits() {
    }

    public static <T> List<T> first(List<T> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (values.size() <= limit) {
            return List.copyOf(values);
        }
        return List.copyOf(values.subList(0, limit));
    }

    public static String text(String value) {
        return text(value, MAX_TEXT_LENGTH);
    }

    public static String text(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + TRUNCATION_MARKER;
    }

    public static boolean exceeds(String value, int maxLength) {
        return value != null && maxLength > 0 && value.length() > maxLength;
    }

    public static BoundedText textList(List<String> values, int maxEntries, int maxLength) {
        if (values == null || values.isEmpty() || maxEntries <= 0) {
            return new BoundedText(List.of(), values != null && !values.isEmpty());
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                cleaned.add(value);
            }
        }
        boolean truncated = cleaned.size() > maxEntries;
        List<String> result = new ArrayList<>();
        for (String value : first(cleaned, maxEntries)) {
            if (exceeds(value, maxLength)) {
                truncated = true;
            }
            result.add(text(value, maxLength));
        }
        return new BoundedText(List.copyOf(result), truncated);
    }

    public record BoundedText(List<String> values, boolean truncated) {
        public BoundedText {
            values = List.copyOf(values == null ? List.of() : values);
        }
    }
}
