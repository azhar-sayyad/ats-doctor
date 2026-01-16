-- Master resume schema (PRD §7.1–§7.3, FEAT-014/015, TASK-034/035).
-- Source of truth is relational; JSONB only for structured AI output.
CREATE TABLE resumes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE resume_versions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id        UUID         NOT NULL REFERENCES resumes (id),
    version          INTEGER      NOT NULL,
    state            VARCHAR(16)  NOT NULL CHECK (state IN ('UPLOADED', 'EXTRACTING', 'PARSING', 'READY', 'FAILED')),
    raw_text         TEXT,
    structured_data  JSONB,
    source_filename  VARCHAR(255),
    model_used       VARCHAR(255),
    model_version    VARCHAR(255),
    prompt_version   VARCHAR(64),
    temperature      NUMERIC,
    error            TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_resume_versions_resume_id_version ON resume_versions (resume_id, version);

CREATE TABLE resume_evidence (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_version_id  UUID        NOT NULL REFERENCES resume_versions (id),
    section            VARCHAR(32) NOT NULL CHECK (section IN ('experience', 'skills', 'projects')),
    section_id         VARCHAR(64),
    text               TEXT        NOT NULL,
    normalized_text    TEXT,
    metadata           JSONB,
    claim_category     VARCHAR(1)  NOT NULL CHECK (claim_category IN ('A', 'B', 'C')),
    source_refs        UUID[],
    created_at         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_resume_evidence_version ON resume_evidence (resume_version_id);

-- embedding (VECTOR(384)) intentionally deferred: no pgvector dependency in MVP
-- (TASK-034 acceptance); lands with FEAT-049 (SPRINT-08+, v0.3+).