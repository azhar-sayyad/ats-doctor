-- FEAT-049 (SPRINT-08+, v0.3+): pgvector embeddings for semantic matching
-- (PRD §5.3, §6.4; embedding column intentionally deferred in V2/TASK-034).
-- Additive and lazy: no rows are written until
-- ats.doctor.matching.semantic-enabled=true (SemanticMatcher seam).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE resume_embeddings (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_version_id UUID         NOT NULL REFERENCES resume_versions (id) ON DELETE CASCADE,
    chunk_type        VARCHAR(32)  NOT NULL CHECK (chunk_type IN ('basics', 'summary', 'skill', 'experience', 'project', 'education')),
    chunk_id          VARCHAR(64),
    content           TEXT         NOT NULL,
    embedding         vector(384)  NOT NULL,
    model_id          VARCHAR(128) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_resume_embeddings_version ON resume_embeddings (resume_version_id);
CREATE INDEX idx_resume_embeddings_hnsw ON resume_embeddings
    USING hnsw (embedding vector_cosine_ops);
