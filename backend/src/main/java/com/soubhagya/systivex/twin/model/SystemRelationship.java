package com.soubhagya.systivex.twin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A directed edge between two SystemEntity records. Self-references and
 * duplicate (source, target, type) triples are rejected by the schema;
 * the service layer rejects self-references before hitting the database.
 */
@Entity
@Table(
        name = "system_relationship",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_system_relationship",
                        columnNames = {"source_entity_id", "target_entity_id", "relationship_type"}))
public class SystemRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_relationship_source"))
    private SystemEntity source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_system_relationship_target"))
    private SystemEntity target;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", length = 32, nullable = false)
    private SystemRelationshipType relationshipType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SystemRelationship() {}

    public SystemRelationship(
            SystemEntity source, SystemEntity target, SystemRelationshipType relationshipType) {
        this.source = source;
        this.target = target;
        this.relationshipType = relationshipType;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public SystemEntity getSource() {
        return source;
    }

    public SystemEntity getTarget() {
        return target;
    }

    public SystemRelationshipType getRelationshipType() {
        return relationshipType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
