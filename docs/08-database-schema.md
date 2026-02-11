# 08 — Database Schema

PostgreSQL schema per PRD v1.2 §7. Relational entities are modeled as rows;
JSONB is used **only** for flexible AI output (`structured_data`,
`score_breakdown`, validation output, generation metadata). Do not collapse
everything into one giant JSON document (PRD §7.1 design note).

**Status**: schema documented from PRD v1.2. `V1__create_ai_runs.sql`
exists (TASK-022, `ai_runs` §1.9). `V2__create_resume_tables.sql`
exists (SPRINT-01, TASK-034/035 — `resumes`, `resume_versions`,
`resume_evidence` §1.1–1.3). `V3__create_job_tables.sql` exists
(SPRINT-02, TASK-044/045 — `jobs`, `job_requirements` §1.4–1.5; check
constraints on `state`/`type`/`importance`). `V4__create_analyses_table.sql`
  - "V5__create_tailoring_tables.sql — tailored_resumes + tailored_changes: PRD §7.7/§7.8; state machine QUEUED → GENERATING → VALIDATING → READY → NEEDS_REVIEW/APPROVED (no FAILED per §5.8 — errors via `error`); `html`/`pdf_path`/`docx_path` NULL until SPRINT-06 export; `tailored_changes.evidence_id` nullable (summary rewrites)"
exists (SPRINT-03, TASK-057 — `analyses` §1.6; check constraint on `state`).
Remaining versioned scripts land in `backend/src/main/resources/db/migration/`
as their features do (DEC-029).

---

## 1. Tables

### 1.1 `resumes`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `name` | VARCHAR | User-given name (e.g., "Master"). |
| `created_at` | TIMESTAMP | Auto-generated. |
| `updated_at` | TIMESTAMP | Auto-updated. |

Feature: FEAT-015 (TASK-035). State machine: Resume (`UPLOADED` → … → `READY`/`FAILED`, PRD §5.8).

### 1.2 `resume_versions`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `resume_id` | UUID | FK → `resumes.id`. |
| `version` | INTEGER | Incremental version number. |
| `state` | VARCHAR | Processing state: `UPLOADED`, `EXTRACTING`, `PARSING`, `READY`, `FAILED` (PRD §5.8). |
| `raw_text` | TEXT | Extracted text from PDF/DOCX. |
| `structured_data` | JSONB | Structured resume (§4.2). |
| `source_filename` | VARCHAR | Original filename. |
| `model_used` | VARCHAR | AI model for parsing. |
| `model_version` | VARCHAR | Exact model revision. |
| `prompt_version` | VARCHAR | Parsing prompt version (`resume-parser-v1`). |
| `temperature` | NUMERIC | Generation temperature. |
| `error` | TEXT | Error detail when `state = FAILED`. |
| `created_at` | TIMESTAMP | Auto-generated. |
| `updated_at` | TIMESTAMP | Auto-updated. |

Feature: FEAT-015 (TASK-035). Unique composite index `(resume_id, version)`.
SPRINT-01 edits update the row in place; append-only versioning (each edit =
new version) lands with FEAT-044, TASK-083.

### 1.3 `resume_evidence`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `resume_version_id` | UUID | FK → `resume_versions.id`. |
| `section` | VARCHAR | `experience`, `skills`, `projects`. |
| `section_id` | VARCHAR | ID within section (e.g., `exp_001`). |
| `text` | TEXT | Raw text (e.g., bullet point). |
| `normalized_text` | TEXT | Lowercase, stemmed. |
| `metadata` | JSONB | Technologies, metrics, domains. |
| `claim_category` | VARCHAR | `A` (fact), `B` (descriptor), `C` (unsupported). |
| `source_refs` | UUID[] | Evidence IDs this claim traces to. |
| `embedding` | VECTOR(384) | Semantic embedding (pgvector, **nullable; v0.3+**). |
| `created_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-014 (TASK-033/034). Claims: A = immutable facts, B = supported
descriptors, C = unsupported claims (PRD §5.1, §5.5, §6.8).

> `embedding` (VECTOR(384)) is **deferred**: no pgvector dependency in the MVP
> (TASK-034 acceptance). The column lands with FEAT-049 (semantic matching,
> SPRINT-08+, v0.3+). `source_refs` is created (UUID[]) and stays empty until
> FEAT-044 wires real evidence references.

### 1.4 `jobs`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `state` | VARCHAR | Processing state: `CREATED`, `EXTRACTING`, `PARSING`, `READY`, `FAILED` (PRD §5.8). |
| `title` | VARCHAR | Job title. |
| `company` | VARCHAR | Company name. |
| `location` | VARCHAR | Job location. |
| `seniority` | VARCHAR | `Junior`, `Mid`, `Senior`, etc. |
| `raw_text` | TEXT | JD text (pasted input or extracted from the file). |
| `structured_data` | JSONB | Structured JD (§4.3). |
| `source_filename` | VARCHAR | Original filename (null for pasted text). |
| `model_used` | VARCHAR | AI model for parsing. |
| `model_version` | VARCHAR | Exact model revision. |
| `prompt_version` | VARCHAR | JD parsing prompt version (`jd-parser-v1`). |
| `temperature` | NUMERIC | Generation temperature. |
| `error` | TEXT | Error detail when `state = FAILED`. |
| `created_at` | TIMESTAMP | Auto-generated. |
| `updated_at` | TIMESTAMP | Auto-updated. |

Feature: FEAT-021 (TASK-045). State machine: Job (`CREATED` → … → `READY`/`FAILED`).
`state`/`error`/`updated_at`/`temperature` are SPRINT-02 additions beyond PRD
§7.4 (same precedent as `resume_versions` V2) so the async pipeline can drive
and report the §5.8 flow.

### 1.5 `job_requirements`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `job_id` | UUID | FK → `jobs.id`. |
| `text` | TEXT | Requirement text. |
| `type` | VARCHAR | `skill`, `experience`, `education`. |
| `importance` | VARCHAR | `high`, `medium`, `low`. |
| `keywords` | VARCHAR[] | Extracted keywords. |
| `embedding` | VECTOR(384) | Semantic embedding (**nullable; v0.3+**). |
| `created_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-020 (TASK-044). Check constraints: `type IN (skill, experience, education)`,
`importance IN (high, medium, low)` — `RequirementExtractor` coalesces AI output to these
values. Keywords are normalized lowercase, deduplicated.

> `embedding` (VECTOR(384)) is **deferred**: no pgvector dependency in the MVP
> (TASK-044 acceptance). The column lands with FEAT-049 (semantic matching,
> SPRINT-08+, v0.3+).

### 1.6 `analyses`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `job_id` | UUID | FK → `jobs.id`. |
| `resume_version_id` | UUID | FK → `resume_versions.id`. |
| `state` | VARCHAR | `QUEUED`, `MATCHING`, `SCORING`, `READY`, `FAILED`. |
| `score` | INTEGER | 0-100 Job Match score. |
| `score_breakdown` | JSONB | Per-category scores + `total`. |
| `matches` | JSONB | Requirement-evidence mappings. |
| `gaps` | JSONB | Unmatched requirements. |
| `generation` | JSONB | Matching/scoring recipe (mode, formula, weights). |
| `error` | TEXT | Error detail when `state = FAILED`. |
| `created_at` | TIMESTAMP | Auto-generated. |
| `updated_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-027 (TASK-057). State machine: Analysis (`QUEUED` → `MATCHING` →
`SCORING` → `READY`/`FAILED`; reanalyze re-enters `QUEUED` from `READY`/`FAILED`).
Note: matches/gaps are **JSONB columns**, not a separate table (PRD §7.1).
Deviations from PRD §7.6 (documented with the V2/V3 precedent): the processing
column is `state` not `status`; `error` and `updated_at` were added so the §5.8
flow can be driven and reported.

### 1.7 `tailored_resumes`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `analysis_id` | UUID | FK → `analyses.id`. |
| `status` | VARCHAR | `QUEUED`, `GENERATING`, `VALIDATING`, `READY`, `NEEDS_REVIEW`, `APPROVED`. |
| `content` | JSONB | Tailored resume JSON. |
| `score_before` | INTEGER | Original Job Match score. |
| `score_after` | INTEGER | Tailored Job Match score. |
| `html` | TEXT | Rendered HTML. |
| `pdf_path` | VARCHAR | Path to exported PDF. |
| `docx_path` | VARCHAR | Path to exported DOCX. |
| `created_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-032 (TASK-065). State machine: Tailoring (`QUEUED` → … → `APPROVED`).

### 1.8 `tailored_changes`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `tailored_resume_id` | UUID | FK → `tailored_resumes.id`. |
| `evidence_id` | UUID | FK → `resume_evidence.id`. |
| `original_text` | TEXT | Original bullet text. |
| `tailored_text` | TEXT | Tailored bullet text. |
| `reason` | VARCHAR | Why the change was made. |
| `claim_category` | VARCHAR | `A`, `B`, or `C` for the changed claim. |
| `status` | VARCHAR | `PENDING`, `ACCEPTED`, `REJECTED`, `EDITED`, `REGENERATED`. |
| `prompt_version` | VARCHAR | Tailoring prompt version used. |
| `created_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-029 (TASK-062), FEAT-037 (TASK-074/075).

### 1.9 `ai_runs`

Every AI call is recorded for auditability and debugging (PRD §6.7):

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key. |
| `task` | VARCHAR | `resume_parser`, `jd_parser`, `requirement_extraction`, `resume_tailoring`, `fact_validation`, `embedding`. |
| `provider` | VARCHAR | `deepseek`, `gemini`, `claude`, `openai`, `ollama`. |
| `model` | VARCHAR | Model name (e.g., `deepseek-v4-flash`). |
| `model_version` | VARCHAR | Exact model revision. |
| `profile` | VARCHAR | Profile used (`cheap`, `quality`, `local`, `private`). |
| `prompt_version` | VARCHAR | Prompt template version. |
| `input_hash` | VARCHAR | SHA-256 of the input (not the raw prompt). |
| `output` | JSONB | Parsed/structured output. |
| `status` | VARCHAR | `success`, `failed`, `fallback`. |
| `error` | TEXT | Error message on failure. |
| `latency_ms` | INTEGER | Duration in ms. |
| `input_tokens` | INTEGER | Prompt token count. |
| `output_tokens` | INTEGER | Completion token count. |
| `created_at` | TIMESTAMP | Auto-generated. |

Feature: FEAT-008 (TASK-022/023).

> Do **not** store sensitive raw prompts unnecessarily — `input_hash` + metadata
> is sufficient for debugging and reproduction (PRD §6.7/§7.1).

---

## 2. Indexes (PRD §7.2)

| Table | Index | Purpose | MVP? |
|-------|-------|---------|------|
| `resume_evidence` | `idx_embedding` (HNSW) | Fast semantic search. | No (v0.3+) |
| `job_requirements` | `idx_embedding` (HNSW) | Fast semantic search. | No (v0.3+) |
| `jobs` | `idx_title` (GIN) | Full-text search on title. | Yes |
| `jobs` | `idx_company` | Filter by company. | Yes |
| `analyses` | `idx_job_id_resume_id` (Composite) | Unique analysis per job/resume. | Yes |

> HNSW embedding indexes are only needed once semantic search ships (v0.3+).
> For the MVP (exact matching only) they can be omitted.

---

## 3. Status enums (PRD §5.8)

| Entity | States |
|--------|--------|
| Resume | `UPLOADED` → `EXTRACTING` → `PARSING` → `READY` / `FAILED` |
| Job | `CREATED` → `EXTRACTING` → `PARSING` → `READY` / `FAILED` |
| Analysis | `QUEUED` → `MATCHING` → `SCORING` → `READY` / `FAILED` |
| Tailored Resume | `QUEUED` → `GENERATING` → `VALIDATING` → `READY` → `NEEDS_REVIEW` → `APPROVED` |

Implemented in `backend/src/main/java/com/atsdoctor/backend/domain/states/StateMachines.java` (FEAT-007, TASK-020) — **not yet written**.

## 4. Relationships (relational, not graph)

```text
resumes 1──N resume_versions 1──N resume_evidence (source_refs → evidence)
jobs    1──N job_requirements
resume_versions 1──N analyses N──1 jobs
analyses 1──N tailored_resumes
tailored_resumes 1──N tailored_changes (evidence_id → resume_evidence.id)
ai_runs ── audit trail for every AI call
```

Master resume is the **source of truth** (immutable); tailoring references a
specific `resume_version_id` and is never chained from another tailored resume
(PRD §5.9).


## 5. Tailoring tables (V5 + V6) — implemented (CL-013, TASK-071)

Implemented with SPRINT-04 (CL-013); PRD §7.7/§7.8.

### tailored_resumes

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| analysis_id | UUID FK → analyses.id | no-chaining: tailoring is never chained from another tailored resume |
| resume_version_id | UUID FK → resume_versions.id | denormalized exact master version (§7.6 rule) |
| state | VARCHAR | `QUEUED`/`GENERATING`/`VALIDATING`/`READY`/`NEEDS_REVIEW`/`APPROVED` (PRD §5.8; `status` renamed to `state` per V2/V3/V4 precedent) |
| content | JSONB | tailored resume doc: `summary`, `experience` (bullets with original/tailored text), `order`, `generation` |
| score_before | INTEGER | analysis score |
| score_after | INTEGER | deterministic MatchPipeline re-run over tailored content |
| validation | JSONB | rule-engine report from the VALIDATING stage / `POST /tailored/{id}/validate` (V6, TASK-071) |
| html / pdf_path / docx_path | TEXT / VARCHAR | NULL until SPRINT-06 export; populated by `ExportService` under `ats.doctor.storage.exports-dir` (CL-017) |
| error | TEXT | failures surface here (no FAILED state) |
| created_at / updated_at | TIMESTAMP | |

### tailored_changes

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| tailored_resume_id | UUID FK → tailored_resumes.id | |
| evidence_id | UUID FK → resume_evidence.id | nullable — summary rewrites |
| original_text / tailored_text | TEXT | |
| reason | VARCHAR | e.g. "Better alignment with JD keywords ('kubernetes')." |
| claim_category | VARCHAR | A / B / C |
| status | VARCHAR | `PENDING` (review lifecycle ACCEPTED/REJECTED/EDITED/REGENERATED ships with FEAT-037, SPRINT-06) |
| prompt_version | VARCHAR | e.g. tailor-bullet-v1 |
| created_at | TIMESTAMP | |
