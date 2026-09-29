-- Phase 1: initial System Twin persistence model.
-- Flyway is the schema authority; Hibernate only validates (ddl-auto=validate).

CREATE TABLE system_entity (
    id              UUID         NOT NULL PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    external_ref    VARCHAR(512),
    environment     VARCHAR(64),
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_system_entity_external_ref UNIQUE (external_ref)
);

CREATE INDEX idx_system_entity_type ON system_entity (type);
CREATE INDEX idx_system_entity_environment ON system_entity (environment);

CREATE TABLE system_relationship (
    id                UUID        NOT NULL PRIMARY KEY,
    source_entity_id  UUID        NOT NULL,
    target_entity_id  UUID        NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_system_relationship_source FOREIGN KEY (source_entity_id)
        REFERENCES system_entity (id) ON DELETE CASCADE,
    CONSTRAINT fk_system_relationship_target FOREIGN KEY (target_entity_id)
        REFERENCES system_entity (id) ON DELETE CASCADE,
    CONSTRAINT chk_system_relationship_no_self_ref CHECK (source_entity_id <> target_entity_id),
    CONSTRAINT uq_system_relationship UNIQUE (source_entity_id, target_entity_id, relationship_type)
);

CREATE INDEX idx_system_relationship_source ON system_relationship (source_entity_id);
CREATE INDEX idx_system_relationship_target ON system_relationship (target_entity_id);
