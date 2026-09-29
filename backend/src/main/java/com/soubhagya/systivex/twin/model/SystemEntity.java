package com.soubhagya.systivex.twin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An object known to the System Twin (service, API, database, ...).
 * Schema is owned by Flyway (see {@code db/migration/V1__create_system_twin.sql});
 * mapping nullability/names must stay aligned with it (ddl-auto=validate).
 */
@Entity
@Table(
        name = "system_entity",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_system_entity_external_ref",
                        columnNames = "external_ref"))
public class SystemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private SystemEntityType type;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "external_ref", length = 512)
    private String externalRef;

    @Column(name = "environment", length = 64)
    private String environment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemEntity() {}

    public SystemEntity(
            SystemEntityType type,
            String name,
            String externalRef,
            String environment,
            Map<String, Object> metadata) {
        this.type = type;
        this.name = name;
        this.externalRef = externalRef;
        this.environment = environment;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public SystemEntityType getType() {
        return type;
    }

    public void setType(SystemEntityType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
