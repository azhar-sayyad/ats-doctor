# 05 — Backlog (Master List)

The **control plane** of the project. Every epic, feature, and task has exactly
one row here. `13-project-status.md` is derived from this file.

Types: `EPIC | FEATURE | TASK | STORY | BUG | SPIKE` (STORY/BUG/SPIKE added as needed).

Statuses: `BACKLOG | PLANNED | READY | IN_PROGRESS | BLOCKED | IN_REVIEW | TESTING | DONE | CANCELLED | DEFERRED`.

Legend — priority: P0 = MVP-blocking, P1 = MVP-desired, P2 = post-MVP.

## EPICs

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| EPIC-001 | EPIC | Foundation | — | — | SPRINT-00 | P0 | DONE | — |
| EPIC-002 | EPIC | Master Resume | — | — | SPRINT-01 | P0 | DONE | EPIC-001 |
| EPIC-003 | EPIC | Job Description Intelligence | — | — | SPRINT-02 | P0 | DONE | EPIC-001 |
| EPIC-004 | EPIC | Matching Engine | — | — | SPRINT-03 | P0 | DONE | EPIC-002, EPIC-003 |
| EPIC-005 | EPIC | Job Match Scoring | — | — | SPRINT-03 | P0 | DONE | EPIC-004 |
| EPIC-006 | EPIC | AI Tailoring | — | — | SPRINT-04 | P0 | DONE | EPIC-005 |
| EPIC-007 | EPIC | Fact Validation & Claim Traceability | — | — | SPRINT-05 | P0 | DONE | EPIC-006 |
| EPIC-008 | EPIC | Resume Review | — | — | SPRINT-06 | P0 | DONE | EPIC-007 |
| EPIC-009 | EPIC | Resume Export | — | — | SPRINT-06 | P0 | DONE | EPIC-008 |
| EPIC-010 | EPIC | History & Versioning | — | — | SPRINT-07 | P1 | READY | EPIC-002, EPIC-009 |
| EPIC-011 | EPIC | Hardening & Reliability | — | — | SPRINT-07 | P1 | READY | EPIC-002, EPIC-003, EPIC-009 |
| EPIC-012 | EPIC | Advanced (Post-MVP) | — | — | SPRINT-08+ | P2 | BACKLOG | EPIC-011 |

## FEATURES

### EPIC-001 Foundation (SPRINT-00)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-001 | FEATURE | Docker Compose Stack & Env Configuration | EPIC-001 | — | SPRINT-00 | P0 | DONE | — |
| FEAT-002 | FEATURE | Spring Boot Backend Skeleton | EPIC-001 | — | SPRINT-00 | P0 | DONE | FEAT-001 |
| FEAT-003 | FEATURE | Next.js Frontend Skeleton | EPIC-001 | — | SPRINT-00 | P0 | DONE | FEAT-002 |
| FEAT-004 | FEATURE | AI Abstraction Layer | EPIC-001 | — | SPRINT-00 | P0 | DONE | — |
| FEAT-005 | FEATURE | AI Service Facade with Retry & Fallback | EPIC-001 | — | SPRINT-00 | P0 | DONE | FEAT-004 |
| FEAT-006 | FEATURE | OmniRoute Deployment Configuration | EPIC-001 | — | SPRINT-00 | P0 | DONE | — |
| FEAT-007 | FEATURE | Processing State Machines | EPIC-001 | — | SPRINT-00 | P0 | DONE | — |
| FEAT-008 | FEATURE | ai_runs Execution Tracking | EPIC-001 | — | SPRINT-00 | P0 | DONE | FEAT-005 |
| FEAT-009 | FEATURE | AI Configuration & Health Endpoints | EPIC-001 | — | SPRINT-00 | P1 | DONE | FEAT-005, FEAT-006 |

### EPIC-002 Master Resume (SPRINT-01)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-010 | FEATURE | Resume Upload | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-001, FEAT-002 |
| FEAT-011 | FEATURE | PDF Text Extraction | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-010 |
| FEAT-012 | FEATURE | DOCX Text Extraction | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-010 |
| FEAT-013 | FEATURE | Resume Structuring (LLM) | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-011, FEAT-012, FEAT-005 |
| FEAT-014 | FEATURE | Resume Evidence Model | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-013 |
| FEAT-015 | FEATURE | Master Resume Persistence | EPIC-002 | — | SPRINT-01 | P0 | DONE | FEAT-014 |
| FEAT-016 | FEATURE | Master Resume Review | EPIC-002 | — | SPRINT-01 | P1 | DONE | FEAT-015 |

### EPIC-003 JD Intelligence (SPRINT-02)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-017 | FEATURE | JD Input | EPIC-003 | — | SPRINT-02 | P0 | DONE | FEAT-002 |
| FEAT-018 | FEATURE | JD Text Extraction | EPIC-003 | — | SPRINT-02 | P0 | DONE | FEAT-017 |
| FEAT-019 | FEATURE | JD Structuring (LLM) | EPIC-003 | — | SPRINT-02 | P0 | DONE | FEAT-018, FEAT-005 |
| FEAT-020 | FEATURE | Requirement & Keyword Extraction | EPIC-003 | — | SPRINT-02 | P0 | DONE | FEAT-019 |
| FEAT-021 | FEATURE | JD Persistence | EPIC-003 | — | SPRINT-02 | P0 | DONE | FEAT-020 |

### EPIC-004 Matching Engine (SPRINT-03)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-022 | FEATURE | Exact/Alias/Keyword Matching | EPIC-004 | — | SPRINT-03 | P0 | DONE | FEAT-015, FEAT-021 |
| FEAT-023 | FEATURE | Requirement → Evidence Mapping | EPIC-004 | — | SPRINT-03 | P0 | DONE | FEAT-022, FEAT-014 |
| FEAT-024 | FEATURE | Phased Matching (exact-first) | EPIC-004 | — | SPRINT-03 | P0 | DONE | FEAT-023 |

### EPIC-005 Job Match Scoring (SPRINT-03)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-025 | FEATURE | Job Match Score | EPIC-005 | — | SPRINT-03 | P0 | DONE | FEAT-024 |
| FEAT-026 | FEATURE | Score Breakdown & Explanation | EPIC-005 | — | SPRINT-03 | P1 | DONE | FEAT-025 |
| FEAT-027 | FEATURE | Analysis Pipeline & Persistence | EPIC-005 | — | SPRINT-03 | P0 | DONE | FEAT-026, FEAT-021 |

### EPIC-006 AI Tailoring (SPRINT-04)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-028 | FEATURE | Selective Tailoring Decision Layer | EPIC-006 | — | SPRINT-04 | P0 | DONE | FEAT-026 |
| FEAT-029 | FEATURE | Bullet-Level Tailoring (LLM) | EPIC-006 | — | SPRINT-04 | P0 | DONE | FEAT-028, FEAT-005 |
| FEAT-030 | FEATURE | Summary Tailoring | EPIC-006 | — | SPRINT-04 | P1 | DONE | FEAT-029 |
| FEAT-031 | FEATURE | Resume Restructuring (reordering) | EPIC-006 | — | SPRINT-04 | P1 | DONE | FEAT-029 |
| FEAT-032 | FEATURE | Tailored Resume Persistence | EPIC-006 | — | SPRINT-04 | P0 | DONE | FEAT-029 |

### EPIC-007 Fact Validation (SPRINT-05)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-033 | FEATURE | Deterministic Validator | EPIC-007 | — | SPRINT-05 | P0 | DONE | FEAT-014, FEAT-029 |
| FEAT-034 | FEATURE | AI Validator | EPIC-007 | — | SPRINT-05 | P0 | DONE | FEAT-033, FEAT-005 |
| FEAT-035 | FEATURE | Validation Rules (A/B/C) | EPIC-007 | — | SPRINT-05 | P0 | DONE | FEAT-034 |
| FEAT-036 | FEATURE | Claim Traceability | EPIC-007 | — | SPRINT-05 | P0 | DONE | FEAT-035, FEAT-014 |

### EPIC-008 Resume Review (SPRINT-06)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-037 | FEATURE | Per-Change Review | EPIC-008 | — | SPRINT-06 | P0 | DONE | FEAT-032 |
| FEAT-038 | FEATURE | Before/After Comparison | EPIC-008 | — | SPRINT-06 | P1 | DONE | FEAT-037 |
| FEAT-039 | FEATURE | Approval Flow (export gate) | EPIC-008 | — | SPRINT-06 | P0 | DONE | FEAT-037 |

### EPIC-009 Resume Export (SPRINT-06)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-040 | FEATURE | HTML Template Rendering | EPIC-009 | — | SPRINT-06 | P0 | DONE | FEAT-039 |
| FEAT-041 | FEATURE | PDF Export | EPIC-009 | — | SPRINT-06 | P0 | DONE | FEAT-040 |
| FEAT-042 | FEATURE | DOCX Export | EPIC-009 | — | SPRINT-06 | P1 | DONE | FEAT-039 |
| FEAT-043 | FEATURE | Export Endpoints & Gating | EPIC-009 | — | SPRINT-06 | P0 | DONE | FEAT-041, FEAT-042 |

### EPIC-010 History & Versioning (SPRINT-07)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-044 | FEATURE | Resume Versioning & Source-of-Truth Enforcement | EPIC-010 | — | SPRINT-07 | P1 | READY | FEAT-015, FEAT-032 |
| FEAT-045 | FEATURE | Analysis History & UI | EPIC-010 | — | SPRINT-07 | P1 | READY | FEAT-027 |

### EPIC-011 Hardening (SPRINT-07)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-046 | FEATURE | Automated Test Suite | EPIC-011 | — | SPRINT-07 | P0 | READY | EPIC-001..EPIC-009 features |
| FEAT-047 | FEATURE | Error Handling & UX Polish | EPIC-011 | — | SPRINT-07 | P1 | READY | — |
| FEAT-048 | FEATURE | Security & Privacy Checklist | EPIC-011 | — | SPRINT-07 | P1 | READY | — |

### EPIC-012 Advanced (SPRINT-08+, BACKLOG)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| FEAT-049 | FEATURE | Semantic Matching (embeddings + pgvector) | EPIC-012 | — | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-050 | FEATURE | Multiple Resume Variants | EPIC-012 | — | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-051 | FEATURE | Cover Letters | EPIC-012 | — | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-052 | FEATURE | Application Tracking | EPIC-012 | — | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-053 | FEATURE | OCR for Scanned PDFs | EPIC-012 | — | SPRINT-08+ | P2 | BACKLOG | FEAT-011 |

## TASKS

### SPRINT-00 (Foundation)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-001 | TASK | docker-compose.yml | EPIC-001 | FEAT-001 | SPRINT-00 | P0 | DONE | — |
| TASK-002 | TASK | .env.example environment template | EPIC-001 | FEAT-001 | SPRINT-00 | P0 | DONE | — |
| TASK-003 | TASK | data/ directory layout + .gitkeep | EPIC-001 | FEAT-001 | SPRINT-00 | P0 | DONE | — |
| TASK-004 | TASK | .gitignore | EPIC-001 | FEAT-001 | SPRINT-00 | P0 | DONE | — |
| TASK-005 | TASK | application.yml + @ConfigurationProperties settings loader | EPIC-001 | FEAT-002 | SPRINT-00 | P0 | DONE | — |
| TASK-006 | TASK | Spring Boot application entry (main class) | EPIC-001 | FEAT-002 | SPRINT-00 | P0 | DONE | TASK-005 |
| TASK-007 | TASK | Health/readiness endpoint | EPIC-001 | FEAT-002 | SPRINT-00 | P0 | DONE | TASK-006 |
| TASK-008 | TASK | Maven wrapper + Dockerfile | EPIC-001 | FEAT-002 | SPRINT-00 | P0 | DONE | — |
| TASK-009 | TASK | Next.js app scaffold | EPIC-001 | FEAT-003 | SPRINT-00 | P0 | DONE | — |
| TASK-010 | TASK | Tailwind + shadcn/ui base setup | EPIC-001 | FEAT-003 | SPRINT-00 | P0 | DONE | TASK-009 |
| TASK-011 | TASK | Dashboard shell + backend health check | EPIC-001 | FEAT-003 | SPRINT-00 | P0 | DONE | TASK-007, TASK-010 |
| TASK-012 | TASK | Model Profiles (ModelProfile.java) | EPIC-001 | FEAT-004 | SPRINT-00 | P0 | DONE | — |
| TASK-013 | TASK | Task Router + ai_tasks.yaml | EPIC-001 | FEAT-004 | SPRINT-00 | P0 | DONE | — |
| TASK-014 | TASK | Prompt templates + versioning (PromptService.java) | EPIC-001 | FEAT-004 | SPRINT-00 | P0 | DONE | — |
| TASK-015 | TASK | AIService providers (OmniRoute + Stub) | EPIC-001 | FEAT-004 | SPRINT-00 | P0 | DONE | TASK-012 |
| TASK-016 | TASK | AI facade with retry + fallback (AiFacade.java) | EPIC-001 | FEAT-005 | SPRINT-00 | P0 | DONE | TASK-013, TASK-015 |
| TASK-017 | TASK | Unit tests: router/profiles/fallback/prompts | EPIC-001 | FEAT-005 | SPRINT-00 | P0 | DONE | TASK-016 |
| TASK-018 | TASK | omniroute/config.yaml | EPIC-001 | FEAT-006 | SPRINT-00 | P0 | DONE | — |
| TASK-019 | TASK | OmniRoute config validation test | EPIC-001 | FEAT-006 | SPRINT-00 | P1 | DONE | TASK-018 |
| TASK-020 | TASK | domain/states enums + transitions | EPIC-001 | FEAT-007 | SPRINT-00 | P0 | DONE | — |
| TASK-021 | TASK | State machine unit tests | EPIC-001 | FEAT-007 | SPRINT-00 | P0 | DONE | TASK-020 |
| TASK-022 | TASK | ai_runs table (migration + ORM) | EPIC-001 | FEAT-008 | SPRINT-00 | P0 | DONE | — |
| TASK-023 | TASK | AI facade recorder integration | EPIC-001 | FEAT-008 | SPRINT-00 | P0 | DONE | TASK-016, TASK-022 |
| TASK-024 | TASK | GET/PUT /ai/config + GET /ai/models endpoints | EPIC-001 | FEAT-009 | SPRINT-00 | P1 | DONE | TASK-016, TASK-018 |
| TASK-025 | TASK | Backend boot + AI layer smoke test | EPIC-001 | FEAT-009 | SPRINT-00 | P0 | DONE | TASK-006, TASK-024 |

### SPRINT-01 (Master Resume)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-026 | TASK | POST /resumes/upload endpoint | EPIC-002 | FEAT-010 | SPRINT-01 | P0 | DONE | — |
| TASK-027 | TASK | File type/size validation | EPIC-002 | FEAT-010 | SPRINT-01 | P0 | DONE | TASK-026 |
| TASK-028 | TASK | Store original file under /data/resumes | EPIC-002 | FEAT-010 | SPRINT-01 | P0 | DONE | TASK-026 |
| TASK-029 | TASK | Apache PDFBox extraction service + tests | EPIC-002 | FEAT-011 | SPRINT-01 | P0 | DONE | TASK-026 |
| TASK-030 | TASK | Apache POI (XWPF) extraction service + tests | EPIC-002 | FEAT-012 | SPRINT-01 | P0 | DONE | TASK-026 |
| TASK-031 | TASK | resume_parser task wiring (resume-parser-v1) | EPIC-002 | FEAT-013 | SPRINT-01 | P0 | DONE | TASK-029, TASK-016 |
| TASK-032 | TASK | Structured resume validation (Pydantic §4.2) | EPIC-002 | FEAT-013 | SPRINT-01 | P0 | DONE | TASK-031 |
| TASK-033 | TASK | Evidence extraction (A/B/C, source_refs) | EPIC-002 | FEAT-014 | SPRINT-01 | P0 | DONE | TASK-032 |
| TASK-034 | TASK | resume_evidence table migration + ORM | EPIC-002 | FEAT-014 | SPRINT-01 | P0 | DONE | TASK-022, TASK-033 |
| TASK-035 | TASK | resumes/resume_versions tables + ORM | EPIC-002 | FEAT-015 | SPRINT-01 | P0 | DONE | TASK-034 |
| TASK-036 | TASK | Resume state machine wiring (→READY/FAILED) | EPIC-002 | FEAT-015 | SPRINT-01 | P0 | DONE | TASK-020, TASK-035 |
| TASK-037 | TASK | Master resume review UI | EPIC-002 | FEAT-016 | SPRINT-01 | P1 | DONE | TASK-035 |
| TASK-038 | TASK | PUT /resumes/{version_id}/edit endpoint | EPIC-002 | FEAT-016 | SPRINT-01 | P1 | DONE | TASK-035 |

### SPRINT-02 (JD Intelligence)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-039 | TASK | POST /jobs endpoint (file or text) | EPIC-003 | FEAT-017 | SPRINT-02 | P0 | DONE | — |
| TASK-040 | TASK | JD text extraction + tests | EPIC-003 | FEAT-018 | SPRINT-02 | P0 | DONE | TASK-029, TASK-030, TASK-039 |
| TASK-041 | TASK | jd_parser task wiring (jd-parser-v1) | EPIC-003 | FEAT-019 | SPRINT-02 | P0 | DONE | TASK-040, TASK-016 |
| TASK-042 | TASK | Structured JD validation (Pydantic §4.3) | EPIC-003 | FEAT-019 | SPRINT-02 | P0 | DONE | TASK-041 |
| TASK-043 | TASK | requirement_extraction task wiring | EPIC-003 | FEAT-020 | SPRINT-02 | P0 | DONE | TASK-041 |
| TASK-044 | TASK | job_requirements table migration + ORM | EPIC-003 | FEAT-020 | SPRINT-02 | P0 | DONE | TASK-043 |
| TASK-045 | TASK | jobs table + ORM + state machine (→READY) | EPIC-003 | FEAT-021 | SPRINT-02 | P0 | DONE | TASK-044 |

### SPRINT-03 (Matching + Scoring)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-046 | TASK | Normalization utilities | EPIC-004 | FEAT-022 | SPRINT-03 | P0 | DONE | — |
| TASK-047 | TASK | Exact/alias/keyword matcher (ExactMatcher) + tests | EPIC-004 | FEAT-022 | SPRINT-03 | P0 | DONE | TASK-046 |
| TASK-048 | TASK | Evidence index & lookup | EPIC-004 | FEAT-023 | SPRINT-03 | P0 | DONE | TASK-047 |
| TASK-049 | TASK | Requirement-evidence matcher + tests | EPIC-004 | FEAT-023 | SPRINT-03 | P0 | DONE | TASK-048 |
| TASK-050 | TASK | Phased matching orchestration (exact-first) | EPIC-004 | FEAT-024 | SPRINT-03 | P0 | DONE | TASK-049 |
| TASK-051 | TASK | Semantic phase stub/flag (deferred hook) | EPIC-004 | FEAT-024 | SPRINT-03 | P1 | DONE | TASK-050 |
| TASK-052 | TASK | Weighted scoring engine + tests | EPIC-005 | FEAT-025 | SPRINT-03 | P0 | DONE | TASK-049 |
| TASK-053 | TASK | Score persistence (analyses.score) | EPIC-005 | FEAT-025 | SPRINT-03 | P0 | DONE | TASK-052 |
| TASK-054 | TASK | score_breakdown generation | EPIC-005 | FEAT-026 | SPRINT-03 | P1 | DONE | TASK-052 |
| TASK-055 | TASK | Strengths/gaps derivation | EPIC-005 | FEAT-026 | SPRINT-03 | P1 | DONE | TASK-054 |
| TASK-056 | TASK | POST /analyses pipeline orchestration | EPIC-005 | FEAT-027 | SPRINT-03 | P0 | DONE | TASK-050 |
| TASK-057 | TASK | analyses table + ORM + state machine | EPIC-005 | FEAT-027 | SPRINT-03 | P0 | DONE | TASK-056 |
| TASK-058 | TASK | GET /analyses endpoints + reanalyze | EPIC-005 | FEAT-027 | SPRINT-03 | P0 | DONE | TASK-057 |

### SPRINT-04 (AI Tailoring)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-059 | TASK | TailorDecider (which bullets to tailor) | EPIC-006 | FEAT-028 | SPRINT-04 | P0 | DONE | TASK-054 |
| TASK-060 | TASK | Decision layer unit tests | EPIC-006 | FEAT-028 | SPRINT-04 | P0 | DONE | TASK-059 |
| TASK-061 | TASK | resume_tailoring task wiring (tailor-bullet-v1) | EPIC-006 | FEAT-029 | SPRINT-04 | P0 | DONE | TASK-059, TASK-016 |
| TASK-062 | TASK | tailored_changes generation | EPIC-006 | FEAT-029 | SPRINT-04 | P0 | DONE | TASK-061 |
| TASK-063 | TASK | Summary rewrite task | EPIC-006 | FEAT-030 | SPRINT-04 | P1 | DONE | TASK-061 |
| TASK-064 | TASK | Reordering/restructuring logic | EPIC-006 | FEAT-031 | SPRINT-04 | P1 | DONE | TASK-062 |
| TASK-065 | TASK | tailored_resumes table + ORM + state machine | EPIC-006 | FEAT-032 | SPRINT-04 | P0 | DONE | TASK-062 |
| TASK-066 | TASK | POST /analyses/{id}/tailor endpoint | EPIC-006 | FEAT-032 | SPRINT-04 | P0 | DONE | TASK-065 |

### SPRINT-05 (Validation + Traceability)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-067 | TASK | Deterministic validator (category A/B) + tests | EPIC-007 | FEAT-033 | SPRINT-05 | P0 | DONE | TASK-062 |
| TASK-068 | TASK | fact_validation task wiring (validator-v1) | EPIC-007 | FEAT-034 | SPRINT-05 | P0 | DONE | TASK-067, TASK-016 |
| TASK-069 | TASK | Validation output normalization | EPIC-007 | FEAT-034 | SPRINT-05 | P0 | DONE | TASK-068 |
| TASK-070 | TASK | Rule engine for A/B/C categories | EPIC-007 | FEAT-035 | SPRINT-05 | P0 | DONE | TASK-067, TASK-069 |
| TASK-071 | TASK | POST /tailored/{id}/validate endpoint | EPIC-007 | FEAT-035 | SPRINT-05 | P0 | DONE | TASK-070 |
| TASK-072 | TASK | source_refs resolution + traceability API | EPIC-007 | FEAT-036 | SPRINT-05 | P0 | DONE | TASK-070 |
| TASK-073 | TASK | Traceability UI ("why is this claim here?") | EPIC-007 | FEAT-036 | SPRINT-05 | P0 | DONE | TASK-072 |

### SPRINT-06 (Review + Export)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-074 | TASK | GET/POST /tailored/{id}/changes endpoints | EPIC-008 | FEAT-037 | SPRINT-06 | P0 | DONE | TASK-065 |
| TASK-075 | TASK | Accept/Reject/Edit/Regenerate logic | EPIC-008 | FEAT-037 | SPRINT-06 | P0 | DONE | TASK-074 |
| TASK-076 | TASK | Per-change review UI | EPIC-008 | FEAT-037 | SPRINT-06 | P0 | DONE | TASK-075 |
| TASK-077 | TASK | Before/after diff view | EPIC-008 | FEAT-038 | SPRINT-06 | P1 | DONE | TASK-076 |
| TASK-078 | TASK | POST /tailored/{id}/approve + export gating | EPIC-008 | FEAT-039 | SPRINT-06 | P0 | DONE | TASK-075 |
| TASK-079 | TASK | Thymeleaf resume template | EPIC-009 | FEAT-040 | SPRINT-06 | P0 | DONE | TASK-078 |
| TASK-080 | TASK | Flying Saucer + OpenPDF PDF generation + tests | EPIC-009 | FEAT-041 | SPRINT-06 | P0 | DONE | TASK-079 |
| TASK-081 | TASK | Apache POI (XWPF) DOCX generation + tests | EPIC-009 | FEAT-042 | SPRINT-06 | P1 | DONE | TASK-078 |
| TASK-082 | TASK | export/pdf\|docx\|json endpoints + 409 gating | EPIC-009 | FEAT-043 | SPRINT-06 | P0 | DONE | TASK-080, TASK-081 |

### SPRINT-07 (History + Hardening)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-083 | TASK | Version increment + immutable versions | EPIC-010 | FEAT-044 | SPRINT-07 | P1 | READY | TASK-035 |
| TASK-084 | TASK | Source-of-truth enforcement (no chaining) | EPIC-010 | FEAT-044 | SPRINT-07 | P1 | READY | TASK-065 |
| TASK-085 | TASK | Analysis history endpoints | EPIC-010 | FEAT-045 | SPRINT-07 | P1 | READY | TASK-057 |
| TASK-086 | TASK | History UI | EPIC-010 | FEAT-045 | SPRINT-07 | P1 | READY | TASK-085 |
| TASK-087 | TASK | CI/test runner (`mvn verify`) + coverage | EPIC-011 | FEAT-046 | SPRINT-07 | P0 | DONE | — |
| TASK-088 | TASK | Integration test fixtures (resumes/JDs) | EPIC-011 | FEAT-046 | SPRINT-07 | P0 | READY | TASK-087 |
| TASK-089 | TASK | Global error handling + retry UX | EPIC-011 | FEAT-047 | SPRINT-07 | P1 | READY | — |
| TASK-090 | TASK | Loading/empty/error states polish | EPIC-011 | FEAT-047 | SPRINT-07 | P1 | READY | TASK-089 |
| TASK-091 | TASK | Security checklist §12.1 implementation | EPIC-011 | FEAT-048 | SPRINT-07 | P1 | READY | — |
| TASK-092 | TASK | AI transparency UI (provider + data-leaves-machine) | EPIC-011 | FEAT-048 | SPRINT-07 | P1 | READY | TASK-024 |

### SPRINT-08+ (Advanced — BACKLOG)

| ID | Type | Title | Epic | Feature | Sprint | Priority | Status | Dependencies |
|----|------|-------|------|---------|--------|----------|--------|--------------|
| TASK-093 | TASK | Embeddings pipeline (sentence-transformers) | EPIC-012 | FEAT-049 | SPRINT-08+ | P2 | BACKLOG | — |
| TASK-094 | TASK | pgvector integration + HNSW indexes | EPIC-012 | FEAT-049 | SPRINT-08+ | P2 | BACKLOG | TASK-093 |
| TASK-095 | TASK | Semantic matcher + fallback | EPIC-012 | FEAT-049 | SPRINT-08+ | P2 | BACKLOG | TASK-094 |
| TASK-096 | TASK | Variant model + UI | EPIC-012 | FEAT-050 | SPRINT-08+ | P2 | BACKLOG | — |
| TASK-097 | TASK | Cover letter generation + export | EPIC-012 | FEAT-051 | SPRINT-08+ | P2 | BACKLOG | — |
| TASK-098 | TASK | Application tracker UI + data | EPIC-012 | FEAT-052 | SPRINT-08+ | P2 | BACKLOG | — |
| TASK-099 | TASK | Tesseract OCR optional integration | EPIC-012 | FEAT-053 | SPRINT-08+ | P2 | BACKLOG | — |

---

## Quick counts (derived)

- EPICs: 12 (9 DONE, 0 IN_PROGRESS, 0 PLANNED, 2 READY, 1 BACKLOG)
- Features: 53 (43 DONE, 0 IN_PROGRESS, 5 READY, 0 PLANNED, 5 BACKLOG)
- Tasks: 99 (83 DONE, 0 IN_PROGRESS, 9 READY, 0 PLANNED, 7 BACKLOG)
