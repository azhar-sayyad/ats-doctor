-- Tailoring schema (PRD §7.7/§7.8, FEAT-032, TASK-065/066).
-- Relational; the flexible tailored-resume document lives in `content` JSONB.
CREATE TABLE tailored_resumes (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id       UUID        NOT NULL REFERENCES analyses (id),
    resume_version_id UUID        NOT NULL REFERENCES resume_versions (id),
    state             VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED', 'GENERATING', 'VALIDATING', 'READY', 'NEEDS_REVIEW', 'APPROVED')),
    content           JSONB,
    score_before      INTEGER,
    score_after       INTEGER,
    html              TEXT,
    pdf_path          VARCHAR(512),
    docx_path         VARCHAR(512),
    error             TEXT,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tailored_resumes_analysis ON tailored_resumes (analysis_id);

CREATE TABLE tailored_changes (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tailored_resume_id UUID        NOT NULL REFERENCES tailored_resumes (id),
    evidence_id       UUID         REFERENCES resume_evidence (id),
    original_text     TEXT         NOT NULL,
    tailored_text     TEXT         NOT NULL,
    reason            VARCHAR(512),
    claim_category    VARCHAR(1)   NOT NULL CHECK (claim_category IN ('A', 'B', 'C')),
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EDITED', 'REGENERATED')),
    prompt_version    VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tailored_changes_resume ON tailored_changes (tailored_resume_id);

-- Deviations from PRD §7.7/§7.8, documented with the V2/V3/V4 precedent:
-- 1. PRD names the processing column `status`; we use `state` consistently
--    with the other pipelines and add `error`/`updated_at` (TailoringState has
--    no FAILED per §5.8 — failures surface via `error`, TASK-089).
-- 2. `resume_version_id` is denormalized onto tailored_resumes (PRD §7.6
--    no-chaining rule: every tailored resume references its exact master
--    version); `html`/`pdf_path`/`docx_path` stay NULL until SPRINT-06 export.
-- 3. `tailored_changes.evidence_id` is nullable — summary rewrites have no
--    single bullet evidence row.