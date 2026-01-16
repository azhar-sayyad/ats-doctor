-- ai_runs audit trail (PRD §7.9 / §6.7, FEAT-008, TASK-022).
-- Raw prompts are never stored — only the SHA-256 of the input.
CREATE TABLE ai_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task           VARCHAR(64)  NOT NULL,
    provider       VARCHAR(64)  NOT NULL,
    model          VARCHAR(255),
    model_version  VARCHAR(255),
    profile        VARCHAR(32),
    prompt_version VARCHAR(64),
    input_hash     VARCHAR(64)  NOT NULL,
    output         JSONB,
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('success', 'failed', 'fallback')),
    error          TEXT,
    latency_ms     INTEGER,
    input_tokens   INTEGER,
    output_tokens  INTEGER,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_runs_task_created ON ai_runs (task, created_at DESC);
