-- JD Intelligence schema (PRD §7.4–§7.5, FEAT-020/021, TASK-044/045).
-- Source of truth is relational; JSONB only for structured AI output (§4.3).
CREATE TABLE jobs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state            VARCHAR(16)  NOT NULL CHECK (state IN ('CREATED', 'EXTRACTING', 'PARSING', 'READY', 'FAILED')),
    title            VARCHAR(255),
    company          VARCHAR(255),
    location         VARCHAR(255),
    seniority        VARCHAR(64),
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

CREATE TABLE job_requirements (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID        NOT NULL REFERENCES jobs (id),
    text       TEXT        NOT NULL,
    type       VARCHAR(16) NOT NULL CHECK (type IN ('skill', 'experience', 'education')),
    importance VARCHAR(8)  NOT NULL CHECK (importance IN ('high', 'medium', 'low')),
    keywords   TEXT[],
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_requirements_job ON job_requirements (job_id);

-- embedding (VECTOR(384)) intentionally deferred: no pgvector dependency in MVP
-- (TASK-044 acceptance); lands with FEAT-049 (SPRINT-08+, v0.3+).
-- PRD §7.4 `jobs` has no state/error/updated_at: state+error added to drive the
-- §5.8 pipeline (same precedent as resume_versions V2); updated_at for parity.