-- Analysis schema (PRD §7.6, FEAT-027, TASK-057).
-- Relational; JSONB only for flexible derived output (score_breakdown,
-- matches, gaps, generation metadata).
CREATE TABLE analyses (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id             UUID        NOT NULL REFERENCES jobs (id),
    resume_version_id  UUID        NOT NULL REFERENCES resume_versions (id),
    state              VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED', 'MATCHING', 'SCORING', 'READY', 'FAILED')),
    score              INTEGER,
    score_breakdown    JSONB,
    matches            JSONB,
    gaps               JSONB,
    generation         JSONB,
    error              TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_analyses_job ON analyses (job_id);
CREATE INDEX idx_analyses_resume_version ON analyses (resume_version_id);

-- Deviations from PRD §7.6, documented with the V2/V3 precedent: PRD names the
-- processing column `status`; we use `state` consistently with the other
-- pipelines and add `error`/`updated_at` so the §5.8 flow can be driven and
-- reported (reanalyze re-enters QUEUED via StateMachines.analysis()).
