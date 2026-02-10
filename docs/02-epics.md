# 02 — Epics

Major product capabilities. Each epic maps to a sprint (see
[`04-sprints.md`](./04-sprints.md)). Features are listed in
[`03-features.md`](./03-features.md).

Status values: `BACKLOG | PLANNED | READY | IN_PROGRESS | BLOCKED | IN_REVIEW | TESTING | DONE | CANCELLED | DEFERRED`.

---

## EPIC-001 — Foundation

```yaml
id: EPIC-001
name: Foundation
status: IN_PROGRESS
priority: P0
sprint: SPRINT-00
owner: dev
dependencies: []
features: [FEAT-001, FEAT-002, FEAT-003, FEAT-004, FEAT-005, FEAT-006, FEAT-007, FEAT-008, FEAT-009]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: A runnable stack (Docker, Spring Boot, Next.js, PostgreSQL) with
  the AI Abstraction Layer, state machines, and `ai_runs` tracking in place.
- **User value**: Everything else builds on this foundation; state machines and
  `ai_runs` are cheap now, painful later (PRD §16).
- **Scope**: docker-compose, env config, backend skeleton + health, frontend
  skeleton, AI Abstraction Layer (AIService/Task Router/Profiles/Prompts),
  facade with retry + fallback, OmniRoute deployment config, state machines,
  `ai_runs` schema + recorder, AI config & health endpoints.
- **Out of scope**: business features (parsing, matching, tailoring).
- **Acceptance criteria**: `docker compose up` boots all services; backend
  `/health` returns OK with AI mode; AI layer resolves profiles and degrades to
  stub in dev; unit tests for the AI layer pass.
- **Risks**: OmniRoute is a fictional/external gateway — the backend must
  degrade gracefully when it is unreachable.

## EPIC-002 — Master Resume

```yaml
id: EPIC-002
name: Master Resume
status: PLANNED
priority: P0
sprint: SPRINT-01
owner: dev
dependencies: [EPIC-001]
features: [FEAT-010, FEAT-011, FEAT-012, FEAT-013, FEAT-014, FEAT-015, FEAT-016]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Upload a master resume, extract text, structure it, build the
  evidence model, and persist it immutably.
- **User value**: The resume is the source of truth for every later step.
- **Scope**: Upload + validation + storage, PDF/DOCX extraction, LLM
  structuring, evidence extraction (claim categories A/B/C), persistence
  (`resumes`/`resume_versions`/`resume_evidence`), review UI.
- **Out of scope**: versioning UX (SPRINT-07), OCR.
- **Acceptance criteria**: `POST /resumes/upload` yields a `READY` resume
  version with structured data and evidence; it is reviewable in the UI.

## EPIC-003 — Job Description Intelligence

```yaml
id: EPIC-003
name: Job Description Intelligence
status: PLANNED
priority: P0
sprint: SPRINT-02
owner: dev
dependencies: [EPIC-001]
features: [FEAT-017, FEAT-018, FEAT-019, FEAT-020, FEAT-021]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Accept a JD (paste or file), structure it, and extract
  requirements + keywords.
- **User value**: Structured requirements drive matching, scoring, and tailoring.
- **Scope**: JD input, text extraction, LLM structuring, requirement/keyword
  extraction, persistence (`jobs`/`job_requirements`).
- **Out of scope**: OCR, multi-page exotic layouts.
- **Acceptance criteria**: `POST /jobs` yields a `READY` job with structured
  data, requirements, and keywords.

## EPIC-004 — Matching Engine

```yaml
id: EPIC-004
name: Matching Engine
status: PLANNED
priority: P0
sprint: SPRINT-03
owner: dev
dependencies: [EPIC-002, EPIC-003]
features: [FEAT-022, FEAT-023, FEAT-024]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Match JD requirements to resume evidence deterministically
  (exact/alias/keyword first).
- **User value**: Explainable, debuggable matches — no black box.
- **Scope**: normalization, exact/alias/keyword matching, requirement→evidence
  mapping, phased orchestration (semantic phase stubbed/deferred).
- **Out of scope**: semantic/embedding matching (deferred to EPIC-012).
- **Acceptance criteria**: ≥80% of requirements correctly matched on the test set.

## EPIC-005 — Job Match Scoring

```yaml
id: EPIC-005
name: Job Match Scoring
status: PLANNED
priority: P0
sprint: SPRINT-03
owner: dev
dependencies: [EPIC-004]
features: [FEAT-025, FEAT-026, FEAT-027]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Weighted, explainable Job Match Score with per-category
  breakdown and strengths/gaps.
- **User value**: Users see *why* a score exists (not an ATS guarantee).
- **Scope**: scoring engine, `score_breakdown`, strengths/gaps derivation,
  analysis pipeline orchestration + persistence.
- **Out of scope**: ATS emulation.
- **Acceptance criteria**: Analysis is `READY` with score + breakdown; UI shows strengths and gaps.

## EPIC-006 — AI Tailoring

```yaml
id: EPIC-006
name: AI Tailoring
status: PLANNED
priority: P0
sprint: SPRINT-04
owner: dev
dependencies: [EPIC-005]
features: [FEAT-028, FEAT-029, FEAT-030, FEAT-031, FEAT-032]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Rewrite **selected** bullets (decision layer) to close gaps
  without inventing facts; produce a tailored resume + per-change record.
- **User value**: Fewer AI calls, lower hallucination risk, user retains control.
- **Scope**: decision layer, bullet rewriting, summary tailoring, restructuring
  (reordering), persistence (`tailored_resumes`/`tailored_changes`).
- **Out of scope**: multi-variant generation (EPIC-012).
- **Acceptance criteria**: Tailoring yields a `tailored_resumes` row with
  `tailored_changes`; each change carries `claim_category` and `reason`.

## EPIC-007 — Fact Validation & Claim Traceability

```yaml
id: EPIC-007
name: Fact Validation & Claim Traceability
status: PLANNED
priority: P0
sprint: SPRINT-05
owner: dev
dependencies: [EPIC-006]
features: [FEAT-033, FEAT-034, FEAT-035, FEAT-036]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Defense-in-depth validation (deterministic → AI → human) and
  traceability from every claim back to source evidence.
- **User value**: Trust. Every claim answers "why is this here?".
- **Scope**: deterministic validator, AI validator, A/B/C rule engine, claim
  traceability resolution + UI.
- **Out of scope**: guaranteeing zero hallucinations.
- **Acceptance criteria**: Unsupported Category A/C claims are flagged; every
  claim resolves to `source_refs`.

## EPIC-008 — Resume Review

```yaml
id: EPIC-008
name: Resume Review
status: PLANNED
priority: P0
sprint: SPRINT-06
owner: dev
dependencies: [EPIC-007]
features: [FEAT-037, FEAT-038, FEAT-039]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Per-change review (Accept/Reject/Edit/Regenerate), before/after
  comparison, and approval before export.
- **User value**: User control is central, not an afterthought.
- **Scope**: change endpoints, review UI, diff view, approval flow + export gate.
- **Out of scope**: batch auto-approve.
- **Acceptance criteria**: Export is blocked (409) until all changes resolved.

## EPIC-009 — Resume Export

```yaml
id: EPIC-009
name: Resume Export
status: PLANNED
priority: P0
sprint: SPRINT-06
owner: dev
dependencies: [EPIC-008]
features: [FEAT-040, FEAT-041, FEAT-042, FEAT-043]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Render approved tailored resumes as HTML/PDF/DOCX/JSON.
- **User value**: Usable, portable output.
- **Scope**: Thymeleaf HTML/CSS template, Flying Saucer + OpenPDF PDF, Apache POI DOCX, export endpoints + gating.
- **Out of scope**: cover letters (EPIC-012).
- **Acceptance criteria**: Exports succeed at 100% for approved resumes.

## EPIC-010 — History & Versioning

```yaml
id: EPIC-010
name: History & Versioning
status: PLANNED
priority: P1
sprint: SPRINT-07
owner: dev
dependencies: [EPIC-002, EPIC-009]
features: [FEAT-044, FEAT-045]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Immutable resume versions + source-of-truth enforcement +
  analysis history.
- **User value**: No resume drift; recoverable history.
- **Scope**: version increment, enforcement, history endpoints + UI.
- **Out of scope**: multi-master-resume management.
- **Acceptance criteria**: Tailoring always references the exact version that
  produced the analysis.

## EPIC-011 — Hardening & Reliability

```yaml
id: EPIC-011
name: Hardening & Reliability
status: PLANNED
priority: P1
sprint: SPRINT-07
owner: dev
dependencies: [EPIC-002, EPIC-003, EPIC-009]
features: [FEAT-046, FEAT-047, FEAT-048]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Tests, robust error handling, UX polish, and the security &
  privacy checklist.
- **User value**: Dependable, private, trustworthy product.
- **Scope**: JUnit 5/Testcontainers suite + Checkstyle, error/retry UX, AI transparency UI, §12 security checklist.
- **Out of scope**: auth (single-user).
- **Acceptance criteria**: CI green; security checklist implemented; fabrication-rate check exercised.

## EPIC-012 — Advanced (Post-MVP)

```yaml
id: EPIC-012
name: Advanced (Post-MVP)
status: BACKLOG
priority: P2
sprint: SPRINT-08+ (future)
owner: dev
dependencies: [EPIC-011]
features: [FEAT-049, FEAT-050, FEAT-051, FEAT-052, FEAT-053]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Objective**: Post-MVP capabilities: semantic matching (embeddings/pgvector),
  multiple variants, cover letters, application tracking, OCR.
- **User value**: Increased match accuracy for ambiguous cases and broader use.
- **Scope**: as listed in the features.
- **Out of scope**: none at this level — all items intentionally deferred.
- **Acceptance criteria**: defined per feature when pulled into a sprint.
- **Risks**: scope creep — keep out of v0.1/v0.2.

---

## Epic dependency graph

```text
EPIC-001 ─┬─→ EPIC-002 ─┬─→ EPIC-004 ─→ EPIC-005 ─→ EPIC-006 ─→ EPIC-007 ─┬─→ EPIC-008 ─→ EPIC-009 ─┬─→ EPIC-010
          └─→ EPIC-003 ─┘              │                                  │                          └─→ EPIC-011
                                       └──────────────────────────────────┴──────────────────────────────────┘
EPIC-012 (future; depends on EPIC-011)
```

Total: **12 epics** (EPIC-001..EPIC-012).
