package com.soubhagya.systivex.twin.model;

/** Directed relationship types between SystemEntity records (Phase 1 set). */
public enum SystemRelationshipType {
    CALLS,
    DEPENDS_ON,
    READS,
    WRITES,
    DEPLOYED_AS,
    EXPOSES,
    CONTAINS,
    CONFIGURES
}
