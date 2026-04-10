# 03 — Features

Feature description, user value, inputs/outputs, and dependencies for every
feature. Detail lives in the PRD and the technical tasks file; this is the
feature inventory plus description/history, where status matches 05-backlog.
**Status truth lives in 05-backlog.md** — this file mirrors it.

> **Note (CL-008)**: this inventory was rebuilt after `03-features.md` was
> truncated by a tooling fault. Statuses are mirrored from `05-backlog.md`;
> task-level acceptance criteria live in `06-technical-tasks.md`; feature
> detail lives in the PRD §5.

---

# FEAT-001 — Docker Compose Stack & Env Configuration

```yaml
id: FEAT-001
name: Docker Compose Stack & Env Configuration
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: []
acceptance_criteria:
  - "docker compose config validates"
  - "services: frontend, backend, db, (omniroute/ollama behind profiles)"
  - "backend is the only service with a ./data mount"
files_or_modules: [docker-compose.yml]
tests: ["docker compose config"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: docker-compose.yml (frontend/backend/db + optional OmniRoute/ollama behind profiles), `.env.example`, `data/` volume dirs, `.gitignore`.
- **User value**: One command (`docker compose up --build`) to launch the whole stack.
- **Inputs**: `docker-compose.yml`, `.env.example`, `data/` | **Outputs**: running services.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-002 — Spring Boot Backend Skeleton

```yaml
id: FEAT-002
name: Spring Boot Backend Skeleton
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-001]
acceptance_criteria:
  - "Spring Boot 3.5.3 app boots without a database"
  - "health/readiness endpoint returns 200 with status + AI mode (omniroute|stub) + db state"
  - "Maven wrapper + Dockerfile"
files_or_modules: [backend/, api/]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: Spring Boot 3.5.3 application: `AppSettings` (@ConfigurationProperties), health/readiness endpoint, Maven wrapper, Dockerfile. Boots offline with no database.
- **User value**: Backend starts and reports its own state without external services.
- **Inputs**: env vars | **Outputs**: running backend on :8000, `GET /api/v1/health`.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-003 — Next.js Frontend Skeleton

```yaml
id: FEAT-003
name: Next.js Frontend Skeleton
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-002]
acceptance_criteria:
  - "Next.js 14 (App Router) app scaffold builds"
  - "dashboard shell renders and calls backend health"
  - "typed API client for /api/v1"
files_or_modules: [frontend/]
tests: ["npm run build"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: Next.js 14 App Router shell with Tailwind/shadcn base, dashboard layout, typed API client, Dockerfile.
- **User value**: UI skeleton that proves end-to-end wiring to the backend.
- **Inputs**: `NEXT_PUBLIC_API_URL` | **Outputs**: running frontend on :3000.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-004 — AI Abstraction Layer

```yaml
id: FEAT-004
name: AI Abstraction Layer
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-002]
acceptance_criteria:
  - "ModelProfile + PROFILE_ env overrides (unknown/self-fallback rejected)"
  - "TaskRouter + ai-tasks.yml with deterministic-task guards"
  - "PromptService with versioned templates"
  - "AIService seam: Stub provider + Spring AI gateway (OmniRoute)"
files_or_modules: [ai/, resources/ai/]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: ModelProfile config + `PROFILE_<name>` env overrides, TaskRouter driven by `ai-tasks.yml`, PromptService with 5 versioned prompts, and the `AIService` seam implemented by Stub and Spring-AI/OmniRoute providers.
- **User value**: Swap AI providers and models through config, never through code.
- **Inputs**: task + input text | **Outputs**: normalized `AiResult` (text, model, trace info).
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-005 — AI Service Facade with Retry & Fallback

```yaml
id: FEAT-005
name: AI Service Facade with Retry & Fallback
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-004]
acceptance_criteria:
  - "single fallback per task (MVP rule), recorded in ai_runs"
  - "retries with backoff bounded by AI_TIMEOUT_SECONDS"
  - "dev/test environments degrade to stub on total failure"
files_or_modules: [ai/AiFacade.java]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: `AiFacade` orchestration: bounded retries with backoff, one fallback per profile (MVP rule), dev/test degrade-to-stub, and a recorder hook for transparency.
- **User value**: AI calls never crash the pipeline.
- **Inputs**: `AiRequest` | **Outputs**: `AIServiceResult`.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-006 — OmniRoute Deployment Configuration

```yaml
id: FEAT-006
name: OmniRoute Deployment Configuration
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: []
acceptance_criteria:
  - "profiles map to providers/models only in deployment config"
  - "DeepSeek is the default cheap/quality provider, swap-able"
  - "local profile uses a text/instruction model"
files_or_modules: [omniroute/config.yaml]
tests: ["config validation test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: `omniroute/config.yaml` maps profiles to providers/models; DeepSeek default, local text-model profile. App code never hardcodes providers.
- **User value**: Deployment-time control over which models serve which tasks.
- **Inputs**: `omniroute/config.yaml` | **Outputs**: validated model routing.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-007 — Processing State Machines

```yaml
id: FEAT-007
name: Processing State Machines
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-002]
acceptance_criteria:
  - "Resume/Job/Analysis/Tailoring states per PRD §5.8 (incl. FAILED on Job)"
  - "invalid transitions rejected"
files_or_modules: [domain/states/]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: State enums and transition maps for Resume, Job, Analysis, and Tailoring per PRD §5.8, with validation of invalid transitions.
- **User value**: Predictable processing lifecycle across all pipelines.
- **Inputs**: state transitions | **Outputs**: validated state changes.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-008 — ai_runs Execution Tracking

```yaml
id: FEAT-008
name: ai_runs Execution Tracking
status: DONE
priority: P0
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-005]
acceptance_criteria:
  - "ai_runs table per §7.9 incl. input_hash; no raw prompt storage"
  - "every AIServiceResult persists as an ai_runs row"
  - "failures never break the caller"
files_or_modules: [db/, repository/]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: `ai_runs` table (Flyway migration + ORM), recorder integration in `AiFacade`, persistence gated by config.
- **User value**: Full AI call transparency without storing raw prompts.
- **Inputs**: `AIServiceResult` | **Outputs**: `ai_runs` rows.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-009 — AI Configuration & Health Endpoints

```yaml
id: FEAT-009
name: AI Configuration & Health Endpoints
status: DONE
priority: P1
epic: EPIC-001
sprint: SPRINT-00
dependencies: [FEAT-005, FEAT-006]
acceptance_criteria:
  - "GET /api/v1/ai/config — task→profile mapping + transparency (provider, data-leaves-machine)"
  - "PUT /api/v1/ai/config — validates profile names before applying"
  - "GET /api/v1/ai/models — profile list with provider/model/fallback"
files_or_modules: [api/ai/]
tests: ["mvn test"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

- **Description**: AI config and transparency endpoints; health endpoint exposes AI mode + DB state.
- **User value**: See and control which models power which tasks at runtime.
- **Inputs**: config requests | **Outputs**: config/models/health responses.
- **Technical notes**: Implemented and verified in SPRINT-00 (CL-008).

---

# FEAT-010 — Resume Upload

```yaml
id: FEAT-010
name: Resume Upload
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-001, FEAT-002]
acceptance_criteria:
  - "POST /resumes/upload accepts multipart PDF/DOCX/TXT ≤10MB; returns resume_version JSON; creates version + resume rows"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeController.java]
tests: ["ResumeControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Upload endpoint + file validation + path-traversal-safe storage under `data/resumes/` (TASK-026/027/028).
- **User value**: Drop in a master resume once; everything else builds on it.
- **Inputs**: multipart file | **Outputs**: `resume_version` JSON at state UPLOADED (async pipeline to READY/FAILED).
- **Technical notes**: Implemented and verified in SPRINT-01 (CL-009). Async + polling: POST returns immediately; clients poll `GET /resumes/{version_id}`.

---

# FEAT-011 — PDF Text Extraction

```yaml
id: FEAT-011
name: PDF Text Extraction
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-010]
acceptance_criteria:
  - "extracts text from standard resumes; graceful error for image-only PDFs"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/PdfParser.java]
tests: ["ParserTest#pdf"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Apache PDFBox 3.0.4 extraction (TASK-029); image-only PDFs rejected with a clear message (OCR is FEAT-053).
- **User value**: Text-based PDFs become machine-readable input.
- **Inputs**: PDF bytes | **Outputs**: extracted text or typed ParseException.

---

# FEAT-012 — DOCX Text Extraction

```yaml
id: FEAT-012
name: DOCX Text Extraction
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-010]
acceptance_criteria:
  - "extracts paragraphs + tables; rejects non-docx"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/DocxParser.java]
tests: ["ParserTest#docx"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Apache POI XWPF extraction of paragraphs and table cells (TASK-030); non-OOXML content rejected.
- **User value**: DOCX master resumes are supported alongside PDF/TXT.

---

# FEAT-013 — Resume Structuring (LLM)

```yaml
id: FEAT-013
name: Resume Structuring (LLM)
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-011, FEAT-012, FEAT-005]
acceptance_criteria:
  - "calls ai.generate(task='resume_parser', input={'resume_text': ...}); result recorded in ai_runs; parse failure → resume FAILED state"
  - "validates output against §4.2 schema"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeDto.java]
tests: ["ResumeDtoTest", "ResumePipelineIntegrationTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: resume_parser wiring (TASK-031), §4.2 Bean-validated DTO with code-fence tolerance (TASK-032); stub provider returns a schema-valid canned resume labeled `_stub` so the pipeline is exercisable offline.
- **User value**: Raw text becomes canonical structured JSON.
- **Inputs**: extracted text | **Outputs**: §4.2 `master.json`, recorded in `ai_runs`.

---

# FEAT-014 — Resume Evidence Model

```yaml
id: FEAT-014
name: Resume Evidence Model
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-013]
acceptance_criteria:
  - "sections (experience/skills/projects) split into evidence with section_id, text, normalized_text, metadata"
  - "claims classified A/B/C; table per §7.3 (embedding deferred, no pgvector in MVP)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/EvidenceExtractor.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/ResumeEvidence.java]
tests: ["EvidenceExtractorTest", "RepositoryTest (integration)"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Deterministic rule-based extraction of evidence rows (TASK-033/034): A = explicit facts, B = accomplishment-verb descriptors, C = unsupported. `source_refs` empty in MVP (populated with FEAT-044); `embedding` column lands with FEAT-049 (pgvector).
- **User value**: Every resume claim is reviewable and traceable for matching (SPRINT-03).

---

# FEAT-015 — Master Resume Persistence

```yaml
id: FEAT-015
name: Master Resume Persistence
status: DONE
priority: P0
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-014]
acceptance_criteria:
  - "resumes/resume_versions tables + ORM; composite (resume_id, version) index; state machine UPLOADED→EXTRACTING→PARSING→READY/FAILED enforced on every mutation"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/resume/ResumeService.java]
tests: ["ResumeServiceTest", "ResumePipelineIntegrationTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: V2 migration + entities/repos (TASK-035), async in-process pipeline with state machine enforcement (TASK-036). `state`/`error` columns on `resume_versions`; edits update in place (append-only versioning is FEAT-044).
- **User value**: Master resume survives restarts; version history starts.
- **Inputs**: upload event | **Outputs**: `resumes`/`resume_versions`/`resume_evidence` rows, `ai_runs` record.

---

# FEAT-016 — Master Resume Review

```yaml
id: FEAT-016
name: Master Resume Review
status: DONE
priority: P1
epic: EPIC-002
sprint: SPRINT-01
dependencies: [FEAT-015]
acceptance_criteria:
  - "review screen shows parsed resume + evidence counts (A/B/C); edit affordance; PUT /resumes/{version_id}/edit re-validates §4.2"
files_or_modules: [frontend/app/(dashboard)/resume/, backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeController.java]
tests: ["ResumeControllerTest#edit", "ResumePipelineIntegrationTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Review UI (TASK-037) — upload form, live state polling, evidence summary, inline editing — plus the edit endpoint (TASK-038) that re-parses/validates and regenerates evidence.
- **User value**: Verify and correct what the AI extracted before it feeds matching/tailoring.

---

# FEAT-017 — JD Input

```yaml
id: FEAT-017
name: JD Input
status: DONE
priority: P0
epic: EPIC-003
sprint: SPRINT-02
dependencies: [FEAT-002]
acceptance_criteria:
  - "POST /jobs accepts a PDF/DOCX/TXT file (≤10MB) or pasted text (≤1MB); returns the job JSON at CREATED"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/jobs/JobController.java]
tests: ["JobControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: `POST /api/v1/jobs` with exactly one of `file` (multipart) or `text` (form field); type/size validation → 400 ProblemDetail, file stored under `data/jobs` (path-traversal-safe, `LocalFileStorage.storeJob`).
- **User value**: Any JD — uploaded or pasted — can seed the pipeline.

---

# FEAT-018 — JD Text Extraction

```yaml
id: FEAT-018
name: JD Text Extraction
status: DONE
priority: P0
epic: EPIC-003
sprint: SPRINT-02
dependencies: [FEAT-017]
acceptance_criteria:
  - "PDF/DOCX/TXT extraction reused for JDs; graceful failure (image-only PDF etc.)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/JdParser.java]
tests: ["JdParserTest#extraction"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: `JdParser.extract` wraps the SPRINT-01 `TextExtractionService`; pasted-text JDs skip extraction (raw text is the input).
- **User value**: Text from any common JD format reaches structuring.

---

# FEAT-019 — JD Structuring (LLM)

```yaml
id: FEAT-019
name: JD Structuring (LLM)
status: DONE
priority: P0
epic: EPIC-003
sprint: SPRINT-02
dependencies: [FEAT-018, FEAT-005]
acceptance_criteria:
  - "jd_parser AI call wired (jd-parser-v1); structured JD validated against §4.3; recorded in ai_runs"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/jobs/JobDto.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/JdParser.java]
tests: ["JobDtoTest", "JdParserTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: `JdParser.structure` calls `AiTask.JD_PARSER`; `JobDto` validates the §4.3 schema (code-fence tolerant, unknown fields ignored, Bean Validation on `job.title`). Stub provider returns a canned §4.3 JD that lines up with the canned resume.
- **User value**: A JD becomes structured, queryable data.

---

# FEAT-020 — Requirement & Keyword Extraction

```yaml
id: FEAT-020
name: Requirement & Keyword Extraction
status: DONE
priority: P0
epic: EPIC-003
sprint: SPRINT-02
dependencies: [FEAT-019]
acceptance_criteria:
  - "requirement_extraction AI call wired; typed requirements (skill/experience/education, high/medium/low) stored in job_requirements"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/RequirementExtractor.java]
tests: ["RequirementExtractorTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: Second stubbed AI call (`requirement_extraction`, extracted from the canonical §4.3 JSON); `RequirementExtractor` normalizes output (lowercased deduped keywords, type/importance coalesced so CHECK constraints can never reject a row). `V3` adds `job_requirements` (keywords TEXT[], embedding deferred to FEAT-049).
- **User value**: SPRINT-03 matching has typed, ranked requirements to match against.

---

# FEAT-021 — JD Persistence

```yaml
id: FEAT-021
name: JD Persistence
status: DONE
priority: P0
epic: EPIC-003
sprint: SPRINT-02
dependencies: [FEAT-020]
acceptance_criteria:
  - "jobs table + state machine (CREATED→EXTRACTING→PARSING→READY/FAILED); list/get/delete endpoints"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/job/JobService.java]
tests: ["JobServiceTest", "JobPipelineIntegrationTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

- **Description**: `Job`/`JobRequirement` entities + repos; async `JobPipeline` (`@Async @EventListener`, after-commit event, `StateMachines.job()` on every mutation); `GET /jobs`, `GET /jobs/{id}`, `DELETE /jobs/{id}` (404 ProblemDetail via `JobExceptionHandler`). All beans gated on `ats.doctor.persistence.enabled`.
- **User value**: JDs persist across restarts and feed SPRINT-03 analysis.

---

# FEAT-022 — Exact/Alias/Keyword Matching

```yaml
id: FEAT-022
name: Exact/Alias/Keyword Matching
status: DONE
priority: P0
epic: EPIC-004
sprint: SPRINT-03
dependencies: [FEAT-015, FEAT-021]
acceptance_criteria:
  - "normalization (lowercase/punctuation/whitespace) used everywhere in matching"
  - "exact containment, token overlap and fuzzy (Commons Text) grades; alias table (js↔javascript, back-end↔backend, …)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/Normalizer.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/ExactMatcher.java]
tests: ["NormalizerTest", "MatcherTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: `Normalizer` (lowercase, punctuation strip keeping `+`/`#`/`.`/`-`, whitespace collapse, token sets), `AliasTable` (whole-phrase + per-token canonicalization), `ExactMatcher` with `EXACT | TOKEN | FUZZY` grades (Commons Text `FuzzyScore`, ratio ≥0.6 vs requirement length). `org.apache.commons:commons-text:1.12.0` added.
- **User value**: JD "JavaScript" still matches resume "JS"; PRD §5.3 exact-matching semantics implemented deterministically.

# FEAT-023 — Requirement → Evidence Mapping

```yaml
id: FEAT-023
name: Requirement → Evidence Mapping
status: DONE
priority: P0
epic: EPIC-004
sprint: SPRINT-03
dependencies: [FEAT-022, FEAT-014]
acceptance_criteria:
  - "in-memory evidence index (token → resume_evidence ids) + capped phrase search"
  - "each JD requirement resolves to evidence ids + match status (matched/partial/unmatched) + similarity"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/EvidenceIndex.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/HybridMatcher.java]
tests: ["MatcherTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: `EvidenceIndex` (token→ids map + linear scan, ≤100 bullets assumption) and `HybridMatcher` (keyword-first search, full-phrase fallback, per-requirement `RequirementMatch` with `EvidenceHit`s capped at 3, status and similarity). Education degrees join the evidence pool as `edu_*` docs.
- **User value**: every requirement match is backed by citable resume evidence (id + text).

# FEAT-024 — Phased Matching (exact-first)

```yaml
id: FEAT-024
name: Phased Matching (exact-first)
status: DONE
priority: P0
epic: EPIC-004
sprint: SPRINT-03
dependencies: [FEAT-023]
acceptance_criteria:
  - "one shared evidence index; exact-first requirements → JD keywords → responsibilities → seniority alignment"
  - "semantic phase is a disabled hook (`ats.doctor.matching.semantic-enabled=false`), no embeddings"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/MatchPipeline.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/SemanticMatcher.java]
tests: ["MatcherTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: `MatchPipeline` orchestrates the phases into a `MatchingResult` (requirement matches, keyword hits, responsibility matches, seniority). `SemanticMatcher` is a real bean consulted only for unmatched requirements when enabled; returns no matches with a warning (FEAT-049 deferred).
- **User value**: explainable exact-first pipeline with a single seam to add embeddings later.

# FEAT-025 — Job Match Score

```yaml
id: FEAT-025
name: Job Match Score
status: DONE
priority: P0
epic: EPIC-005
sprint: SPRINT-03
dependencies: [FEAT-024]
acceptance_criteria:
  - "weighted 0–100 score: skills .30, keywords .20, responsibilities .20, experience .15, seniority .10, education .05 (PRD §5.3)"
  - "importance factors high 1.0 / medium 0.75 / low 0.5; partial counts 0.5"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/scoring/ScoreCalculator.java]
tests: ["ScoreCalculatorTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: `ScoreCalculator` with in-code weight/importance constants (SPRINT-03 decision: deterministic, documented) and neutral (100) categories when a category has no requirements.
- **User value**: the single 0–100 Job Match Score the product surfaces.

# FEAT-026 — Score Breakdown & Explanation

```yaml
id: FEAT-026
name: Score Breakdown & Explanation
status: DONE
priority: P1
epic: EPIC-005
sprint: SPRINT-03
dependencies: [FEAT-025]
acceptance_criteria:
  - "`score_breakdown` per §4.4 (6 categories × {score, weight, matched[], missing[]}) plus a `total` key"
  - "strengths/gaps lists; gaps carry deterministic suggestions"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/scoring/ScoreCalculator.java, backend/src/main/java/com/atsdoctor/backend/application/analysis/AnalysisPipeline.java]
tests: ["ScoreCalculatorTest", "AnalysisPipelineIntegrationTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: breakdown JSON (score, weight, matched/missing texts per category + `total`), strengths (matched requirements/keywords/responsibilities/seniority) and gaps (unmatched + two deterministic suggestions each, stop-word-filtered keyword).
- **User value**: users see exactly why the score is what it is and what to add.

# FEAT-027 — Analysis Pipeline & Persistence

```yaml
id: FEAT-027
name: Analysis Pipeline & Persistence
status: DONE
priority: P0
epic: EPIC-005
sprint: SPRINT-03
dependencies: [FEAT-026, FEAT-021]
acceptance_criteria:
  - "`POST /analyses` queues; analyses persisted (QUEUED→MATCHING→SCORING→READY/FAILED, V4 `analyses` table); GET list/byId; POST reanalyze (READY/FAILED→QUEUED)"
  - "deterministic matching records no ai_runs rows"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/analysis/AnalysisService.java, backend/src/main/java/com/atsdoctor/backend/api/analyses/AnalysisController.java]
tests: ["AnalysisServiceTest", "AnalysisControllerTest", "AnalysisPipelineIntegrationTest"]
created_at: 2026-08-13
updated_at: 2026-08-13
```

- **Description**: `analyses` table (V4), `Analysis` entity + repo, `AnalysisService` (queue/load/mark/complete/reanalyze, every mutation through `StateMachines.analysis()` which gained READY→QUEUED and FAILED→QUEUED), async `AnalysisPipeline` (`@Async @EventListener` after-commit), snake_case `AnalysisResponse` with re-parsed JSONB, `AnalysisController` (`POST /analyses`, `GET /analyses`, `GET /analyses/{id}`, `POST /analyses/{id}/reanalyze`) + `AnalysisExceptionHandler` (400/404/409 ProblemDetail).
- **User value**: one endpoint produces a complete, explainable, re-runnable analysis of a job against a resume version.

---

## Remaining features (SPRINT-04+)

Full per-feature blocks for future sprints live in `05-backlog.md` (statuses) and
`06-technical-tasks.md` (task acceptance criteria); detail lives in the PRD §5.
SPRINT-00..02 features (FEAT-001..021) have blocks above.

| ID | Name | Epic | Sprint | Priority | Status | Dependencies |
|----|------|------|--------|----------|--------|--------------|
| FEAT-028 | Selective Tailoring Decision Layer | EPIC-006 | SPRINT-04 | P0 | DONE | FEAT-026 |
| FEAT-029 | Bullet-Level Tailoring (LLM) | EPIC-006 | SPRINT-04 | P0 | DONE | FEAT-028, FEAT-005 |
| FEAT-030 | Summary Tailoring | EPIC-006 | SPRINT-04 | P1 | DONE | FEAT-029 |
| FEAT-031 | Resume Restructuring (reordering) | EPIC-006 | SPRINT-04 | P1 | DONE | FEAT-029 |
| FEAT-032 | Tailored Resume Persistence | EPIC-006 | SPRINT-04 | P0 | DONE | FEAT-029 |
| FEAT-033 | Deterministic Validator | EPIC-007 | SPRINT-05 | P0 | DONE | FEAT-014, FEAT-029 |
| FEAT-034 | AI Validator | EPIC-007 | SPRINT-05 | P0 | DONE | FEAT-033, FEAT-005 |
| FEAT-035 | Validation Rules (A/B/C) | EPIC-007 | SPRINT-05 | P0 | DONE | FEAT-034 |
| FEAT-036 | Claim Traceability | EPIC-007 | SPRINT-05 | P0 | DONE | FEAT-035, FEAT-014 |
| FEAT-037 | Per-Change Review | EPIC-008 | SPRINT-06 | P0 | DONE | FEAT-032 |
| FEAT-038 | Before/After Comparison | EPIC-008 | SPRINT-06 | P1 | DONE | FEAT-037 |
| FEAT-039 | Approval Flow (export gate) | EPIC-008 | SPRINT-06 | P0 | DONE | FEAT-037 |
| FEAT-040 | HTML Template Rendering | EPIC-009 | SPRINT-06 | P0 | DONE | FEAT-039 |
| FEAT-041 | PDF Export | EPIC-009 | SPRINT-06 | P0 | DONE | FEAT-040 |
| FEAT-042 | DOCX Export | EPIC-009 | SPRINT-06 | P1 | DONE | FEAT-039 |
| FEAT-043 | Export Endpoints & Gating | EPIC-009 | SPRINT-06 | P0 | DONE | FEAT-041, FEAT-042 |
| FEAT-044 | Resume Versioning & Source-of-Truth Enforcement | EPIC-010 | SPRINT-07 | P1 | READY | FEAT-015, FEAT-032 |
| FEAT-045 | Analysis History & UI | EPIC-010 | SPRINT-07 | P1 | READY | FEAT-027 |
| FEAT-046 | Automated Test Suite | EPIC-011 | SPRINT-07 | P0 | READY | EPIC-001..EPIC-009 features |
| FEAT-047 | Error Handling & UX Polish | EPIC-011 | SPRINT-07 | P1 | READY | — |
| FEAT-048 | Security & Privacy Checklist | EPIC-011 | SPRINT-07 | P1 | READY | — |
| FEAT-049 | Semantic Matching (embeddings + pgvector) | EPIC-012 | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-050 | Multiple Resume Variants | EPIC-012 | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-051 | Cover Letters | EPIC-012 | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-052 | Application Tracking | EPIC-012 | SPRINT-08+ | P2 | BACKLOG | — |
| FEAT-053 | OCR for Scanned PDFs | EPIC-012 | SPRINT-08+ | P2 | BACKLOG |
| FEAT-054 | Export Template Selection | EPIC-009 | SPRINT-07 | P1 | DONE | FEAT-040, FEAT-043 |


## FEAT-028 — Selective Tailoring Decision Layer

- Feature: EPIC-006 / SPRINT-04 / P0 / FEAT-026 → FEAT-029 (05-backlog: DONE, CL-013)
- What: chooses which bullets need tailoring from the gap analysis (skips
  already-covered bullets; cap 3 rewrites; summary rewrite when a gap exists).
- Status in 05-backlog: DONE (CL-013).

## FEAT-029 — Bullet-Level Tailoring (LLM)

- Feature: EPIC-006 / SPRINT-04 / P0 / FEAT-028 → FEAT-030 (05-backlog: DONE, CL-013)
- What: per-bullet AI rewrite via `ai.generate(task='resume_tailoring')`
  (`tailor-bullet-v1` A/B/C prompt); each call recorded in `ai_runs`;
  `tailored_changes` rows PENDING with original/tailored text, reason,
  claim_category, prompt_version.
- Status in 05-backlog: DONE (CL-013).

## FEAT-030 — Summary Tailoring

- Feature: EPIC-006 / SPRINT-04 / P1 / FEAT-029 → FEAT-031 (05-backlog: DONE, CL-013)
- What: summary variant of the bullet rewrite (same prompt, summary as the
  "bullet"); change row with `evidence_id` NULL.
- Status in 05-backlog: DONE (CL-013).

## FEAT-031 — Resume Restructuring (reordering)

- Feature: EPIC-006 / SPRINT-04 / P1 / FEAT-030 → FEAT-032 (05-backlog: DONE, CL-013)
- What: citation-weighted reorder of experience sections (most-relevant
  first); evidence-preserving and reversible.
- Status in 05-backlog: DONE (CL-013).

## FEAT-032 — Tailored Resume Persistence

- Feature: EPIC-006 / SPRINT-04 / P0 / FEAT-029 → FEAT-033 (05-backlog: DONE, CL-013)
- What: `V5__create_tailoring_tables.sql` (`tailored_resumes` per PRD §7.7 +
  `tailored_changes` per §7.8); `POST /analyses/{id}/tailor` +
  `GET /tailored/{id}`; QUEUED → GENERATING → VALIDATING → READY with
  deterministic `score_after`; no FAILED (errors via `error`, §5.8).
- Status in 05-backlog: DONE (CL-013).
