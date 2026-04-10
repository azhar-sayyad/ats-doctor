# 06 — Technical Tasks

Engineering tasks grouped by feature. Every task maps to one feature and one
sprint. Statuses: `BACKLOG | PLANNED | READY | IN_PROGRESS | BLOCKED | IN_REVIEW | TESTING | DONE | CANCELLED | DEFERRED`.

Estimates: t-shirt sizes (XS/S/M/L/XL). Files/modules refer to the PRD §14 tree.

**Status note**: SPRINT-00..06 (EPIC-001..009) are implemented and verified —
TASK-001..082 are DONE (built, tested, and smoke-checked; 248/248 backend
tests green via the `mvn verify` gate). SPRINT-07 is ACTIVE (TASK-087 DONE;
TASK-083..086, 088..092 READY). SPRINT-08+ stays BACKLOG.

---

# SPRINT-00 — Foundation (EPIC-001)

## FEAT-001 — Docker Compose Stack & Env Configuration

### TASK-001 — docker-compose.yml
```yaml
id: TASK-001
title: docker-compose.yml
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-001
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "`docker compose config` validates"
  - "services: frontend, backend, db, (omniroute/ollama behind profiles)"
  - "backend is the only service with a ./data mount"
files_or_modules: [docker-compose.yml]
tests: ["docker compose config"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-002 — .env.example environment template
```yaml
id: TASK-002
title: .env.example environment template
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-001
sprint: SPRINT-00
dependencies: []
estimate: S
acceptance_criteria:
  - "documents every env var used by compose + backend"
  - "no secrets committed; placeholders only"
files_or_modules: [.env.example]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-003 — data/ directory layout + .gitkeep
```yaml
id: TASK-003
title: data/ directory layout + .gitkeep
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-001
sprint: SPRINT-00
dependencies: []
estimate: XS
acceptance_criteria:
  - "data/{resumes,jobs,analyses,outputs} exist with .gitkeep"
files_or_modules: [data/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-004 — .gitignore
```yaml
id: TASK-004
title: .gitignore
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-001
sprint: SPRINT-00
dependencies: []
estimate: XS
acceptance_criteria:
  - "ignores .env, node_modules, .next, target/, data contents, ollama models"
files_or_modules: [.gitignore]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-002 — Spring Boot Backend Skeleton

### TASK-005 — application.yml + @ConfigurationProperties settings loader
```yaml
id: TASK-005
title: application.yml + @ConfigurationProperties settings loader
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-002
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "loads all settings from env with sane defaults"
  - "no pydantic-settings dependency (plain dataclass)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/config/AtsDoctorProperties.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-006 — Spring Boot application entry + lifecycle
```yaml
id: TASK-006
title: Spring Boot application entry + lifecycle
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-002
sprint: SPRINT-00
dependencies: [TASK-005]
estimate: M
acceptance_criteria:
  - "app boots with Spring Boot without a database"
  - "lifespan calls configure_ai(...) and (lazily) creates the DB engine"
  - "routers mounted under /api/v1"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/AtsDoctorApplication.java]
tests: ["app boot smoke"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-007 — Health/readiness endpoint
```yaml
id: TASK-007
title: Health/readiness endpoint
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-002
sprint: SPRINT-00
dependencies: [TASK-006]
estimate: S
acceptance_criteria:
  - "`GET /api/v1/health` returns 200 with status + AI mode (omniroute|stub) + db state"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/health/HealthController.java]
tests: ["HealthControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-008 — Maven wrapper + Dockerfile
```yaml
id: TASK-008
title: Maven wrapper + Dockerfile
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-002
sprint: SPRINT-00
dependencies: []
estimate: S
acceptance_criteria:
  - "java:21-jre; builds and runs the Spring Boot fat jar on 8000"
  - "creates /data subdirectories"
files_or_modules: [backend/mvnw, backend/pom.xml, backend/Dockerfile]
tests: ["docker build backend"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-003 — Next.js Frontend Skeleton

### TASK-009 — Next.js app scaffold
```yaml
id: TASK-009
title: Next.js app scaffold
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-003
sprint: SPRINT-00
dependencies: []
estimate: L
acceptance_criteria:
  - "Next.js 14 + TypeScript app; app router only, no API routes (PRD §14)"
files_or_modules: [frontend/]
tests: ["npm run build"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-010 — Tailwind + shadcn/ui base setup
```yaml
id: TASK-010
title: Tailwind + shadcn/ui base setup
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-003
sprint: SPRINT-00
dependencies: [TASK-009]
estimate: M
acceptance_criteria:
  - "design tokens + base components installed and themed"
files_or_modules: [frontend/components, frontend/styles]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-011 — Dashboard shell + backend health check
```yaml
id: TASK-011
title: Dashboard shell + backend health check
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-003
sprint: SPRINT-00
dependencies: [TASK-007, TASK-010]
estimate: M
acceptance_criteria:
  - "dashboard shell renders; shows backend connection + AI mode"
  - "NEXT_PUBLIC_API_URL drives the API base"
files_or_modules: [frontend/app/(dashboard)/page.tsx]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-004 — AI Abstraction Layer

### TASK-012 — Model Profiles (ModelProfile.java)
```yaml
id: TASK-012
title: Model Profiles (ModelProfile.java)
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-004
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "resolves profile → provider/model; env overridable (PROFILE_<name>)"
  - "single fallback per profile (§6.4); rejects unknown/self-fallbacks"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/ModelProfile.java]
tests: ["AiLayerTest#profiles"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-013 — Task Router + ai-tasks.yml
```yaml
id: TASK-013
title: Task Router + ai-tasks.yml
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-004
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "routes tasks (resume_parser, jd_parser, requirement_extraction, resume_tailoring, fact_validation, embedding)"
  - "rejects deterministic tasks (exact_matching, scoring, extraction, export)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/TaskRouter.java, backend/src/main/resources/ai-tasks.yml]
tests: ["AiLayerTest#router"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-014 — Prompt templates + versioning (PromptService.java)
```yaml
id: TASK-014
title: Prompt templates + versioning (PromptService.java)
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-004
sprint: SPRINT-00
dependencies: []
estimate: S
acceptance_criteria:
  - "templates for resume-parser-v1, jd-parser-v1, requirement-extraction-v1, tailor-bullet-v1, validator-v1"
  - "missing-variable errors surface clearly"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/PromptService.java]
tests: ["AiLayerTest#prompts"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-015 — AIService providers (OmniRoute + Stub)
```yaml
id: TASK-015
title: AIService providers (OmniRoute + Stub)
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-004
sprint: SPRINT-00
dependencies: [TASK-012]
estimate: L
acceptance_criteria:
  - "OmniRoute provider POSTs OpenAI-compatible chat/completions; captures provider/model/usage"
  - "Stub provider is deterministic and labeled provider='stub'"
  - "vendor SDKs never appear"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/AIService.java, backend/src/main/java/com/atsdoctor/backend/api/ai/AiDto.java]
tests: ["AiLayerTest#providers"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-005 — AI Service Facade

### TASK-016 — AI facade with retry + fallback
```yaml
id: TASK-016
title: AI facade with retry + fallback (AiFacade.java)
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-005
sprint: SPRINT-00
dependencies: [TASK-013, TASK-015]
estimate: M
acceptance_criteria:
  - "`generate(task, input)` is the only app-facing call"
  - "retries with backoff within AI_TIMEOUT_SECONDS; one fallback per task"
  - "dev/test degrade to stub on total failure; results labeled"
  - "recorder hook invoked for every result"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/AiFacade.java]
tests: ["AiLayerTest#facade"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-017 — Unit tests: router/profiles/fallback/prompts
```yaml
id: TASK-017
title: Unit tests: router/profiles/fallback/prompts
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-005
sprint: SPRINT-00
dependencies: [TASK-016]
estimate: M
acceptance_criteria:
  - "≥90% coverage on application/ai; stub provider path covered; fallback chain covered"
files_or_modules: [backend/src/test/java/com/atsdoctor/backend/AiLayerTest.java]
tests: ["mvn test -Dtest=AiLayerTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-006 — OmniRoute Deployment Configuration

### TASK-018 — omniroute/config.yaml
```yaml
id: TASK-018
title: omniroute/config.yaml
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-006
sprint: SPRINT-00
dependencies: []
estimate: S
acceptance_criteria:
  - "maps profiles (cheap/quality/local/private) to providers/models"
  - "DeepSeek is the default for cheap/quality; local uses a text/instruction model"
  - "secrets injected via env, never hard-coded"
files_or_modules: [omniroute/config.yaml]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-019 — OmniRoute config validation test
```yaml
id: TASK-019
title: OmniRoute config validation test
status: DONE
priority: P1
epic: EPIC-001
feature: FEAT-006
sprint: SPRINT-00
dependencies: [TASK-018]
estimate: S
acceptance_criteria:
  - "config.yaml parses; profiles/providers valid; every profile has a model"
files_or_modules: [backend/src/test/java/com/atsdoctor/backend/AiLayerTest.java]
tests: ["mvn test -Dtest=AiLayerTest#omnirouteConfig"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-007 — Processing State Machines

### TASK-020 — domain/states enums + transitions
```yaml
id: TASK-020
title: domain/states enums + transitions
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-007
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "Resume/Job/Analysis/Tailoring states per §5.8 (incl. FAILED on Job)"
  - "transition() rejects invalid moves"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/domain/states/StateMachines.java]
tests: ["StateMachineTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-021 — State machine unit tests
```yaml
id: TASK-021
title: State machine unit tests
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-007
sprint: SPRINT-00
dependencies: [TASK-020]
estimate: S
acceptance_criteria:
  - "valid chains pass; invalid transitions raise; FAILED is terminal"
files_or_modules: [backend/src/test/java/com/atsdoctor/backend/StateMachineTest.java]
tests: ["mvn test -Dtest=StateMachineTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-008 — ai_runs Execution Tracking

### TASK-022 — ai_runs table (migration + ORM)
```yaml
id: TASK-022
title: ai_runs table (migration + ORM)
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-008
sprint: SPRINT-00
dependencies: []
estimate: M
acceptance_criteria:
  - "table per §7.9 incl. input_hash; no raw prompt storage"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/, backend/src/main/resources/db/migration/]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-023 — AI facade recorder integration
```yaml
id: TASK-023
title: AI facade recorder integration
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-008
sprint: SPRINT-00
dependencies: [TASK-016, TASK-022]
estimate: S
acceptance_criteria:
  - "every AIServiceResult persists as an ai_runs row; failures never break the caller"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/ai/AiFacade.java]
tests: ["AiRunsTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-009 — AI Configuration & Health Endpoints

### TASK-024 — GET/PUT /ai/config + GET /ai/models endpoints
```yaml
id: TASK-024
title: GET/PUT /ai/config + GET /ai/models endpoints
status: DONE
priority: P1
epic: EPIC-001
feature: FEAT-009
sprint: SPRINT-00
dependencies: [TASK-016, TASK-018]
estimate: M
acceptance_criteria:
  - "returns task→profile mapping + transparency (provider, data-leaves-machine)"
  - "PUT validates profile names before applying"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/ai/AiConfigController.java]
tests: ["AiConfigControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-025 — Backend boot + AI layer smoke test
```yaml
id: TASK-025
title: Backend boot + AI layer smoke test
status: DONE
priority: P0
epic: EPIC-001
feature: FEAT-009
sprint: SPRINT-00
dependencies: [TASK-006, TASK-024]
estimate: S
acceptance_criteria:
  - "Spring Boot app boots; /api/v1/health 200; AI layer resolves profiles and runs a stub generation"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/]
tests: ["smoke script"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

---

# SPRINT-01 — Master Resume (EPIC-002)

## FEAT-010 — Resume Upload

### TASK-026 — POST /resumes/upload endpoint
```yaml
id: TASK-026
title: POST /resumes/upload endpoint
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-010
sprint: SPRINT-01
dependencies: []
estimate: M
acceptance_criteria:
  - "accepts multipart file; returns resume_version JSON; creates version + resume rows"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeController.java]
tests: ["ResumeControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-027 — File type/size validation
```yaml
id: TASK-027
title: File type/size validation
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-010
sprint: SPRINT-01
dependencies: [TASK-026]
estimate: S
acceptance_criteria:
  - "PDF/DOCX/TXT only; ≤10MB; 400 with clear message otherwise"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/files/LocalFileStorage.java]
tests: ["ResumeControllerTest#validation"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-028 — Store original file under /data/resumes
```yaml
id: TASK-028
title: Store original file under /data/resumes
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-010
sprint: SPRINT-01
dependencies: [TASK-026]
estimate: S
acceptance_criteria:
  - "file stored with safe generated name; path traversal prevented"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/files/LocalFileStorage.java]
tests: ["FileStorageTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-011 / FEAT-012 — PDF & DOCX Text Extraction

### TASK-029 — Apache PDFBox extraction service + tests
```yaml
id: TASK-029
title: Apache PDFBox extraction service + tests
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-011
sprint: SPRINT-01
dependencies: [TASK-026]
estimate: M
acceptance_criteria:
  - "extracts text from standard resumes; graceful error for image-only PDFs"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/PdfParser.java]
tests: ["ParserTest#pdf"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-030 — Apache POI (XWPF) extraction service + tests
```yaml
id: TASK-030
title: Apache POI (XWPF) extraction service + tests
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-012
sprint: SPRINT-01
dependencies: [TASK-026]
estimate: M
acceptance_criteria:
  - "extracts paragraphs + tables; rejects non-docx"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/DocxParser.java]
tests: ["ParserTest#docx"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-013 — Resume Structuring

### TASK-031 — resume_parser task wiring (resume-parser-v1)
```yaml
id: TASK-031
title: resume_parser task wiring (resume-parser-v1)
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-013
sprint: SPRINT-01
dependencies: [TASK-029, TASK-016]
estimate: M
acceptance_criteria:
  - "calls ai.generate(task='resume_parser', input={'resume_text': ...})"
  - "result recorded in ai_runs; parse failure → resume FAILED state"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/ResumeParser.java]
tests: ["ResumeParserTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-032 — Structured resume validation (Pydantic §4.2)
```yaml
id: TASK-032
title: Structured resume validation (Pydantic §4.2)
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-013
sprint: SPRINT-01
dependencies: [TASK-031]
estimate: S
acceptance_criteria:
  - "validates output against §4.2 schema; invalid output triggers retry/reject"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeDto.java]
tests: ["ResumeDtoTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-014 — Resume Evidence Model

### TASK-033 — Evidence extraction (A/B/C, source_refs)
```yaml
id: TASK-033
title: Evidence extraction (A/B/C, source_refs)
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-014
sprint: SPRINT-01
dependencies: [TASK-032]
estimate: L
acceptance_criteria:
  - "sections (experience/skills/projects) split into evidence with section_id, text, normalized_text, metadata"
  - "claims classified A/B/C with source_refs"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/EvidenceExtractor.java]
tests: ["EvidenceExtractorTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-034 — resume_evidence table migration + ORM
```yaml
id: TASK-034
title: resume_evidence table migration + ORM
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-014
sprint: SPRINT-01
dependencies: [TASK-022, TASK-033]
estimate: M
acceptance_criteria:
  - "table per §7.3 (embedding column nullable; no pgvector dependency in MVP)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/ResumeEvidence.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-015 — Master Resume Persistence

### TASK-035 — resumes/resume_versions tables + ORM
```yaml
id: TASK-035
title: resumes/resume_versions tables + ORM
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-015
sprint: SPRINT-01
dependencies: [TASK-034]
estimate: M
acceptance_criteria:
  - "tables per §7.1/§7.2; versioned rows append-only; composite index"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/ResumeVersion.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-036 — Resume state machine wiring (→READY/FAILED)
```yaml
id: TASK-036
title: Resume state machine wiring (→READY/FAILED)
status: DONE
priority: P0
epic: EPIC-002
feature: FEAT-015
sprint: SPRINT-01
dependencies: [TASK-020, TASK-035]
estimate: M
acceptance_criteria:
  - "UPLOADED→EXTRACTING→PARSING→READY/FAILED enforced on every mutation"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/resume/ResumeService.java]
tests: ["ResumeServiceTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-016 — Master Resume Review

### TASK-037 — Master resume review UI
```yaml
id: TASK-037
title: Master resume review UI
status: DONE
priority: P1
epic: EPIC-002
feature: FEAT-016
sprint: SPRINT-01
dependencies: [TASK-035]
estimate: M
acceptance_criteria:
  - "review screen shows parsed resume + evidence counts; edit affordance"
files_or_modules: [frontend/app/(dashboard)/resume/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-038 — PUT /resumes/{version_id}/edit endpoint
```yaml
id: TASK-038
title: PUT /resumes/{version_id}/edit endpoint
status: DONE
priority: P1
epic: EPIC-002
feature: FEAT-016
sprint: SPRINT-01
dependencies: [TASK-035]
estimate: S
acceptance_criteria:
  - "updates structured_data; re-validates against §4.2"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/resume/ResumeController.java]
tests: ["ResumeControllerTest#edit"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

---

# SPRINT-02 — JD Intelligence (EPIC-003)

## FEAT-017 — JD Input

### TASK-039 — POST /jobs endpoint (file or text)
```yaml
id: TASK-039
title: POST /jobs endpoint (file or text)
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-017
sprint: SPRINT-02
dependencies: []
estimate: M
acceptance_criteria:
  - "accepts text or file; returns job JSON (queued)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/jobs/JobController.java]
tests: ["JobControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-018 — JD Text Extraction

### TASK-040 — JD text extraction + tests
```yaml
id: TASK-040
title: JD text extraction + tests
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-018
sprint: SPRINT-02
dependencies: [TASK-029, TASK-030, TASK-039]
estimate: S
acceptance_criteria:
  - "reuses PDF/DOCX/TXT extraction for JDs; graceful failure"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/JdParser.java]
tests: ["JdParserTest#extraction"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-019 — JD Structuring

### TASK-041 — jd_parser task wiring (jd-parser-v1)
```yaml
id: TASK-041
title: jd_parser task wiring (jd-parser-v1)
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-019
sprint: SPRINT-02
dependencies: [TASK-040, TASK-016]
estimate: M
acceptance_criteria:
  - "calls ai.generate(task='jd_parser'); recorded in ai_runs"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/JdParser.java]
tests: ["JdParserTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-042 — Structured JD validation (Pydantic §4.3)
```yaml
id: TASK-042
title: Structured JD validation (Pydantic §4.3)
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-019
sprint: SPRINT-02
dependencies: [TASK-041]
estimate: S
acceptance_criteria:
  - "validates §4.3 schema; invalid output triggers retry/reject"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/jobs/JobDto.java]
tests: ["JobDtoTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-020 — Requirement & Keyword Extraction

### TASK-043 — requirement_extraction task wiring
```yaml
id: TASK-043
title: requirement_extraction task wiring
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-020
sprint: SPRINT-02
dependencies: [TASK-041]
estimate: M
acceptance_criteria:
  - "calls ai.generate(task='requirement_extraction'); requirements typed skill/experience/education with importance"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/RequirementExtractor.java]
tests: ["RequirementExtractorTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-044 — job_requirements table migration + ORM
```yaml
id: TASK-044
title: job_requirements table migration + ORM
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-020
sprint: SPRINT-02
dependencies: [TASK-043]
estimate: M
acceptance_criteria:
  - "table per §7.5 (embedding nullable, no pgvector in MVP)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/Job.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-021 — JD Persistence

### TASK-045 — jobs table + ORM + state machine (→READY)
```yaml
id: TASK-045
title: jobs table + ORM + state machine (→READY)
status: DONE
priority: P0
epic: EPIC-003
feature: FEAT-021
sprint: SPRINT-02
dependencies: [TASK-044]
estimate: M
acceptance_criteria:
  - "jobs persisted (CREATED→EXTRACTING→PARSING→READY/FAILED); list/get/delete endpoints"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/job/JobService.java]
tests: ["JobServiceTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

---

# SPRINT-03 — Matching + Scoring (EPIC-004, EPIC-005)

## FEAT-022 — Exact/Alias/Keyword Matching

### TASK-046 — Normalization utilities
```yaml
id: TASK-046
title: Normalization utilities
status: DONE
priority: P0
epic: EPIC-004
feature: FEAT-022
sprint: SPRINT-03
dependencies: []
estimate: S
acceptance_criteria:
  - "lowercase, stem/lemming, whitespace normalization used everywhere"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/Normalizer.java]
tests: ["NormalizerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-047 — Exact/alias/keyword matcher + tests
```yaml
id: TASK-047
title: Exact/alias/keyword matcher (ExactMatcher) + tests
status: DONE
priority: P0
epic: EPIC-004
feature: FEAT-022
sprint: SPRINT-03
dependencies: [TASK-046]
estimate: M
acceptance_criteria:
  - "exact, alias (JS↔JavaScript), and keyword matching; alias table maintained"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/ExactMatcher.java]
tests: ["MatcherTest#exact"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-023 — Requirement → Evidence Mapping

### TASK-048 — Evidence index & lookup
```yaml
id: TASK-048
title: Evidence index & lookup
status: DONE
priority: P0
epic: EPIC-004
feature: FEAT-023
sprint: SPRINT-03
dependencies: [TASK-047]
estimate: M
acceptance_criteria:
  - "efficient lookup of evidence by keyword/alias for 100+ bullets"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/EvidenceIndex.java]
tests: ["MatcherTest#index"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-049 — Requirement-evidence matcher + tests
```yaml
id: TASK-049
title: Requirement-evidence matcher + tests
status: DONE
priority: P0
epic: EPIC-004
feature: FEAT-023
sprint: SPRINT-03
dependencies: [TASK-048]
estimate: M
acceptance_criteria:
  - "each requirement → evidence IDs + similarity; unmatched → gaps"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/HybridMatcher.java]
tests: ["MatcherTest#mapping"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-024 — Phased Matching

### TASK-050 — Phased matching orchestration (exact-first)
```yaml
id: TASK-050
title: Phased matching orchestration (exact-first)
status: DONE
priority: P0
epic: EPIC-004
feature: FEAT-024
sprint: SPRINT-03
dependencies: [TASK-049]
estimate: M
acceptance_criteria:
  - "runs exact phase for all requirements; unresolved items flow to semantic (deferred)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/MatchPipeline.java]
tests: ["MatcherTest#phases"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-051 — Semantic phase stub/flag (deferred hook)
```yaml
id: TASK-051
title: Semantic phase stub/flag (deferred hook)
status: DONE
priority: P1
epic: EPIC-004
feature: FEAT-024
sprint: SPRINT-03
dependencies: [TASK-050]
estimate: S
acceptance_criteria:
  - "semantic phase present as disabled hook; no embeddings in MVP"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/SemanticMatcher.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-025 — Job Match Score

### TASK-052 — Weighted scoring engine + tests
```yaml
id: TASK-052
title: Weighted scoring engine + tests
status: DONE
priority: P0
epic: EPIC-005
feature: FEAT-025
sprint: SPRINT-03
dependencies: [TASK-049]
estimate: M
acceptance_criteria:
  - "0–100 score weighted by importance (high>medium>low) and match confidence"
  - "deterministic; same inputs → same score"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/scoring/ScoreCalculator.java]
tests: ["ScoreCalculatorTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-053 — Score persistence (analyses.score)
```yaml
id: TASK-053
title: Score persistence (analyses.score)
status: DONE
priority: P0
epic: EPIC-005
feature: FEAT-025
sprint: SPRINT-03
dependencies: [TASK-052]
estimate: S
acceptance_criteria:
  - "score persisted on the analyses row"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/Analysis.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-026 — Score Breakdown & Explanation

### TASK-054 — score_breakdown generation
```yaml
id: TASK-054
title: score_breakdown generation
status: DONE
priority: P1
epic: EPIC-005
feature: FEAT-026
sprint: SPRINT-03
dependencies: [TASK-052]
estimate: S
acceptance_criteria:
  - "per-category breakdown JSON persisted"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/scoring/ScoreCalculator.java]
tests: ["ScoreCalculatorTest#breakdown"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-055 — Strengths/gaps derivation
```yaml
id: TASK-055
title: Strengths/gaps derivation
status: DONE
priority: P1
epic: EPIC-005
feature: FEAT-026
sprint: SPRINT-03
dependencies: [TASK-054]
estimate: S
acceptance_criteria:
  - "strengths (matched) + gaps (unmatched) with suggestions derivable for UI"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/scoring/ScoreCalculator.java]
tests: ["ScoreCalculatorTest#gaps"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-027 — Analysis Pipeline & Persistence

### TASK-056 — POST /analyses pipeline orchestration
```yaml
id: TASK-056
title: POST /analyses pipeline orchestration
status: DONE
priority: P0
epic: EPIC-005
feature: FEAT-027
sprint: SPRINT-03
dependencies: [TASK-050]
estimate: L
acceptance_criteria:
  - "queues + runs match→score pipeline; returns analysis with status"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/analysis/AnalysisService.java]
tests: ["AnalysisServiceTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-057 — analyses table + ORM + state machine
```yaml
id: TASK-057
title: analyses table + ORM + state machine
status: DONE
priority: P0
epic: EPIC-005
feature: FEAT-027
sprint: SPRINT-03
dependencies: [TASK-056]
estimate: M
acceptance_criteria:
  - "table per §7.6; QUEUED→MATCHING→SCORING→READY/FAILED"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/Analysis.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-058 — GET /analyses endpoints + reanalyze
```yaml
id: TASK-058
title: GET /analyses endpoints + reanalyze
status: DONE
priority: P0
epic: EPIC-005
feature: FEAT-027
sprint: SPRINT-03
dependencies: [TASK-057]
estimate: M
acceptance_criteria:
  - "GET /analyses/{id}, GET /analyses list, POST /analyses/{id}/reanalyze"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/analyses/AnalysisController.java]
tests: ["AnalysisControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

---

# SPRINT-04 — AI Tailoring (EPIC-006)

## FEAT-028 — Selective Tailoring Decision Layer

### TASK-059 — TailorDecider (which bullets to tailor)
```yaml
id: TASK-059
title: TailorDecider (which bullets to tailor)
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-028
sprint: SPRINT-04
dependencies: [TASK-054]
estimate: M
acceptance_criteria:
  - "selects bullets to rewrite from gaps/overlaps; skips already-matched bullets"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/TailorDecider.java]
tests: ["TailorDeciderTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-060 — Decision layer unit tests
```yaml
id: TASK-060
title: Decision layer unit tests
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-028
sprint: SPRINT-04
dependencies: [TASK-059]
estimate: S
acceptance_criteria:
  - "≥90% coverage; no-op when nothing needs tailoring"
files_or_modules: [backend/src/test/java/com/atsdoctor/backend/TailorDeciderTest.java]
tests: ["mvn test -Dtest=TailorDeciderTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-029 — Bullet-Level Tailoring

### TASK-061 — resume_tailoring task wiring (tailor-bullet-v1)
```yaml
id: TASK-061
title: resume_tailoring task wiring (tailor-bullet-v1)
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-029
sprint: SPRINT-04
dependencies: [TASK-059, TASK-016]
estimate: M
acceptance_criteria:
  - "calls ai.generate(task='resume_tailoring') per selected bullet with evidence + requirements"
  - "output validated against A/B/C rules"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/BulletRewriter.java]
tests: ["BulletRewriterTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-062 — tailored_changes generation
```yaml
id: TASK-062
title: tailored_changes generation
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-029
sprint: SPRINT-04
dependencies: [TASK-061]
estimate: S
acceptance_criteria:
  - "each change stores original_text, tailored_text, reason, claim_category, prompt_version, status=PENDING"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/TailoredResume.java]
tests: ["TailoredResumeTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-030 — Summary Tailoring

### TASK-063 — Summary rewrite task
```yaml
id: TASK-063
title: Summary rewrite task
status: DONE
priority: P1
epic: EPIC-006
feature: FEAT-030
sprint: SPRINT-04
dependencies: [TASK-061]
estimate: M
acceptance_criteria:
  - "summary rewritten against JD keywords with A/B/C constraints"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/BulletRewriter.java]
tests: ["BulletRewriterTest#summary"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-031 — Resume Restructuring

### TASK-064 — Reordering/restructuring logic
```yaml
id: TASK-064
title: Reordering/restructuring logic
status: DONE
priority: P1
epic: EPIC-006
feature: FEAT-031
sprint: SPRINT-04
dependencies: [TASK-062]
estimate: M
acceptance_criteria:
  - "most-relevant experience reordered first; evidence-preserving and reversible"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/SummaryRestructurer.java]
tests: ["SummaryRestructurerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-032 — Tailored Resume Persistence

### TASK-065 — tailored_resumes table + ORM + state machine
```yaml
id: TASK-065
title: tailored_resumes table + ORM + state machine
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-032
sprint: SPRINT-04
dependencies: [TASK-062]
estimate: M
acceptance_criteria:
  - "table per §7.7; QUEUED→GENERATING→VALIDATING→READY→NEEDS_REVIEW; score_before/after stored"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/TailoredResume.java]
tests: ["RepositoryTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-066 — POST /analyses/{id}/tailor endpoint
```yaml
id: TASK-066
title: POST /analyses/{id}/tailor endpoint
status: DONE
priority: P0
epic: EPIC-006
feature: FEAT-032
sprint: SPRINT-04
dependencies: [TASK-065]
estimate: M
acceptance_criteria:
  - "runs decision + tailoring pipeline; returns tailored_resume JSON"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/tailor/TailoringController.java]
tests: ["TailoringControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

---

# SPRINT-05 — Fact Validation & Claim Traceability (EPIC-007)

## FEAT-033 — Deterministic Validator

### TASK-067 — Deterministic validator (category A/B) + tests
```yaml
id: TASK-067
title: Deterministic validator (category A/B) + tests
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-033
sprint: SPRINT-05
dependencies: [TASK-062]
estimate: L
acceptance_criteria:
  - "flags new technologies/metrics/titles (A) and unsupported descriptors (B) without an LLM"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/DeterministicValidator.java]
tests: ["DeterministicValidatorTest#validate"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-034 — AI Validator

### TASK-068 — fact_validation task wiring (validator-v1)
```yaml
id: TASK-068
title: fact_validation task wiring (validator-v1)
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-034
sprint: SPRINT-05
dependencies: [TASK-067, TASK-016]
estimate: M
acceptance_criteria:
  - "calls ai.generate(task='fact_validation') with original + tailored resume"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/FactValidator.java]
tests: ["FactValidatorTest#ai"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-069 — Validation output normalization
```yaml
id: TASK-069
title: Validation output normalization
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-034
sprint: SPRINT-05
dependencies: [TASK-068]
estimate: S
acceptance_criteria:
  - "AI output normalized to is_valid + issues[{type,text,original_evidence,suggestion}]"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/tailor/ValidationDto.java]
tests: ["FactValidatorTest#normalization"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-035 — Validation Rules (A/B/C)

### TASK-070 — Rule engine for A/B/C categories
```yaml
id: TASK-070
title: Rule engine for A/B/C categories
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-035
sprint: SPRINT-05
dependencies: [TASK-067, TASK-069]
estimate: M
acceptance_criteria:
  - "combines deterministic + AI findings; types issues; decides verdict"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/DeterministicValidator.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/AiValidator.java, backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/ValidationRules.java]
tests: ["ValidationRulesTest#validate"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-071 — POST /tailored/{id}/validate endpoint
```yaml
id: TASK-071
title: POST /tailored/{id}/validate endpoint
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-035
sprint: SPRINT-05
dependencies: [TASK-070]
estimate: S
acceptance_criteria:
  - "returns validation JSON; updates tailored state to VALIDATING→READY"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/validation/ValidationService.java, backend/src/main/java/com/atsdoctor/backend/api/tailor/TailoringController.java, backend/src/main/resources/db/migration/V6__add_validation_to_tailored_resumes.sql]
tests: ["ValidationControllerTest", "ValidationServiceTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-036 — Claim Traceability

### TASK-072 — source_refs resolution + traceability API
```yaml
id: TASK-072
title: source_refs resolution + traceability API
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-036
sprint: SPRINT-05
dependencies: [TASK-070]
estimate: M
acceptance_criteria:
  - "endpoint resolves a claim → supporting evidence items (source_refs)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/validation/TraceabilityService.java, backend/src/main/java/com/atsdoctor/backend/api/tailor/TailoringController.java]
tests: ["TraceabilityServiceTest", "TraceabilityControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-073 — Traceability UI ("why is this claim here?")
```yaml
id: TASK-073
title: Traceability UI ("why is this claim here?")
status: DONE
priority: P0
epic: EPIC-007
feature: FEAT-036
sprint: SPRINT-05
dependencies: [TASK-072]
estimate: M
acceptance_criteria:
  - "clickable claims show supporting evidence (core MVP feature, not future)"
files_or_modules: [frontend/app/tailored/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-13
```

---

# SPRINT-06 — Review + Export (EPIC-008, EPIC-009)

## FEAT-037 — Per-Change Review

### TASK-074 — GET/POST /tailored/{id}/changes endpoints
```yaml
id: TASK-074
title: GET/POST /tailored/{id}/changes endpoints
status: READY
priority: P0
epic: EPIC-008
feature: FEAT-037
sprint: SPRINT-06
dependencies: [TASK-065]
estimate: M
acceptance_criteria:
  - "POST applies an action to a change; lists changes with review state"
notes:
  - "the read-only GET listing shipped earlier (SPRINT-05, traceability-drawer dependency) — SPRINT-06 adds the POST action lifecycle"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/tailor/TailoringController.java]
tests: ["ChangesControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-075 — Accept/Reject/Edit/Regenerate logic
```yaml
id: TASK-075
title: Accept/Reject/Edit/Regenerate logic
status: READY
priority: P0
epic: EPIC-008
feature: FEAT-037
sprint: SPRINT-06
dependencies: [TASK-074]
estimate: M
acceptance_criteria:
  - "change status transitions PENDING→ACCEPTED|REJECTED|EDITED|REGENERATED; regenerate re-runs the AI task"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/tailoring/TailoredResumeService.java]
tests: ["TailoredResumeServiceTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-076 — Per-change review UI
```yaml
id: TASK-076
title: Per-change review UI
status: READY
priority: P0
epic: EPIC-008
feature: FEAT-037
sprint: SPRINT-06
dependencies: [TASK-075]
estimate: L
acceptance_criteria:
  - "original/proposed/why/evidence per change; Accept/Reject/Edit/Regenerate controls"
files_or_modules: [frontend/app/tailored/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-038 — Before/After Comparison

### TASK-077 — Before/after diff view
```yaml
id: TASK-077
title: Before/after diff view
status: READY
priority: P1
epic: EPIC-008
feature: FEAT-038
sprint: SPRINT-06
dependencies: [TASK-076]
estimate: S
acceptance_criteria:
  - "side-by-side original vs tailored; score before/after shown"
files_or_modules: [frontend/app/tailored/, frontend/components/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-039 — Approval Flow

### TASK-078 — POST /tailored/{id}/approve + export gating
```yaml
id: TASK-078
title: POST /tailored/{id}/approve + export gating
status: READY
priority: P0
epic: EPIC-008
feature: FEAT-039
sprint: SPRINT-06
dependencies: [TASK-075]
estimate: S
acceptance_criteria:
  - "approve sets APPROVED (409 until all changes resolved + warnings cleared); export enforces gate"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/tailoring/TailoredResumeService.java]
tests: ["ApproveFlowTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-040 — HTML Template

### TASK-079 — Thymeleaf resume template
```yaml
id: TASK-079
title: Thymeleaf resume template
status: READY
priority: P0
epic: EPIC-009
feature: FEAT-040
sprint: SPRINT-06
dependencies: [TASK-078]
estimate: M
acceptance_criteria:
  - "renders approved tailored resume; output HTML is sanitized"
files_or_modules: [backend/src/main/resources/templates/, backend/src/main/java/com/atsdoctor/backend/infrastructure/export/ExportService.java]
tests: ["ExportServiceTest#html"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-041 — PDF Export

### TASK-080 — Flying Saucer + OpenPDF PDF generation + tests
```yaml
id: TASK-080
title: Flying Saucer + OpenPDF PDF generation + tests
status: READY
priority: P0
epic: EPIC-009
feature: FEAT-041
sprint: SPRINT-06
dependencies: [TASK-079]
estimate: M
acceptance_criteria:
  - "HTML→PDF at 100% success on test set; pdf_path persisted"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/export/ExportService.java]
tests: ["ExportServiceTest#pdf"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-042 — DOCX Export

### TASK-081 — Apache POI (XWPF) DOCX generation + tests
```yaml
id: TASK-081
title: Apache POI (XWPF) DOCX generation + tests
status: READY
priority: P1
epic: EPIC-009
feature: FEAT-042
sprint: SPRINT-06
dependencies: [TASK-078]
estimate: M
acceptance_criteria:
  - "editable .docx generated; docx_path persisted"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/export/ExportService.java]
tests: ["ExportServiceTest#docx"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

## FEAT-043 — Export Endpoints & Gating

### TASK-082 — export/pdf|docx|json endpoints + 409 gating
```yaml
id: TASK-082
title: export/pdf|docx|json endpoints + 409 gating
status: READY
priority: P0
epic: EPIC-009
feature: FEAT-043
sprint: SPRINT-06
dependencies: [TASK-080, TASK-081]
estimate: S
acceptance_criteria:
  - "endpoints return files; 409 when not approved; 404 when export missing"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/export/ExportController.java]
tests: ["ExportControllerTest"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

---

# SPRINT-07 — History + Versioning + Hardening (EPIC-010, EPIC-011)

## FEAT-044 — Resume Versioning & Source-of-Truth Enforcement

### TASK-083 — Version increment + immutable versions
```yaml
id: TASK-083
title: Version increment + immutable versions
status: READY
priority: P1
epic: EPIC-010
feature: FEAT-044
sprint: SPRINT-07
dependencies: [TASK-035]
estimate: M
acceptance_criteria:
  - "uploads create new immutable versions; old versions never mutated"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/resume/ResumeService.java]
tests: ["VersioningTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-084 — Source-of-truth enforcement (no chaining)
```yaml
id: TASK-084
title: Source-of-truth enforcement (no chaining)
status: READY
priority: P1
epic: EPIC-010
feature: FEAT-044
sprint: SPRINT-07
dependencies: [TASK-065]
estimate: M
acceptance_criteria:
  - "analysis stores resume_version_id; tailoring from tailored resumes is rejected (PRD §5.9)"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/application/analysis/AnalysisService.java]
tests: ["SourceOfTruthTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-045 — Analysis History & UI

### TASK-085 — Analysis history endpoints
```yaml
id: TASK-085
title: Analysis history endpoints
status: READY
priority: P1
epic: EPIC-010
feature: FEAT-045
sprint: SPRINT-07
dependencies: [TASK-057]
estimate: S
acceptance_criteria:
  - "list analyses with metadata; detail reachable; reanalyze works"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/analyses/AnalysisController.java]
tests: ["AnalysisControllerTest#history"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-086 — History UI
```yaml
id: TASK-086
title: History UI
status: READY
priority: P1
epic: EPIC-010
feature: FEAT-045
sprint: SPRINT-07
dependencies: [TASK-085]
estimate: M
acceptance_criteria:
  - "dashboard recent analyses list + navigation to past analyses"
files_or_modules: [frontend/app/(dashboard)/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-046 — Automated Test Suite

### TASK-087 — CI/test runner (Maven) + coverage
```yaml
id: TASK-087
title: CI/test runner (Maven) + coverage
status: DONE
priority: P0
epic: EPIC-011
feature: FEAT-046
sprint: SPRINT-07
dependencies: []
estimate: M
acceptance_criteria:
  - "Maven build green (`mvn verify`); coverage targets per §13.1 (parser 90%, matcher 90%, tailor 80%, validator 100%)"
files_or_modules: [backend/pom.xml, .github/workflows/ci.yml]
tests: ["mvn verify"]
created_at: 2026-08-12
updated_at: 2026-08-13
```

### TASK-088 — Integration test fixtures (resumes/JDs)
```yaml
id: TASK-088
title: Integration test fixtures (resumes/JDs)
status: READY
priority: P0
epic: EPIC-011
feature: FEAT-046
sprint: SPRINT-07
dependencies: [TASK-087]
estimate: L
acceptance_criteria:
  - "10+ resumes, 20+ JDs, edge cases (empty, tables/images, no skills) — §13.4"
  - "end-to-end flows: upload→analyze→tailor→validate→export; fabrication detection; multi-JD isolation"
files_or_modules: [backend/tests/fixtures/]
tests: ["mvn verify (Testcontainers)"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-047 — Error Handling & UX Polish

### TASK-089 — Global error handling + retry UX
```yaml
id: TASK-089
title: Global error handling + retry UX
status: READY
priority: P1
epic: EPIC-011
feature: FEAT-047
sprint: SPRINT-07
dependencies: []
estimate: M
acceptance_criteria:
  - "unified error responses; AI failures show provider/fallback info; no silent failures"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/api/, frontend/components/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-090 — Loading/empty/error states polish
```yaml
id: TASK-090
title: Loading/empty/error states polish
status: READY
priority: P1
epic: EPIC-011
feature: FEAT-047
sprint: SPRINT-07
dependencies: [TASK-089]
estimate: M
acceptance_criteria:
  - "all screens handle loading/empty/error per 10-ui-ux-specification.md"
files_or_modules: [frontend/app/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-048 — Security & Privacy Checklist

### TASK-091 — Security checklist §12.1 implementation
```yaml
id: TASK-091
title: Security checklist §12.1 implementation
status: READY
priority: P1
epic: EPIC-011
feature: FEAT-048
sprint: SPRINT-07
dependencies: []
estimate: M
acceptance_criteria:
  - "input validation, file type/size limits, path traversal prevention, parameterized SQL, HTML sanitization, localhost binding"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/files/, backend/src/main/java/com/atsdoctor/backend/api/]
tests: ["SecurityTest"]
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-092 — AI transparency UI (provider + data-leaves-machine)
```yaml
id: TASK-092
title: AI transparency UI (provider + data-leaves-machine)
status: READY
priority: P1
epic: EPIC-011
feature: FEAT-048
sprint: SPRINT-07
dependencies: [TASK-024]
estimate: S
acceptance_criteria:
  - "settings shows provider per task and 'Data leaves this machine: Yes/No' (§12.2)"
files_or_modules: [frontend/components/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

---

# SPRINT-08+ — Advanced (EPIC-012, BACKLOG)

## FEAT-049 — Semantic Matching

### TASK-093 — Embeddings pipeline (sentence-transformers)
```yaml
id: TASK-093
title: Embeddings pipeline (sentence-transformers)
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-049
sprint: SPRINT-08+
dependencies: []
estimate: L
acceptance_criteria:
  - "embeds evidence + requirements; batch, cached; model configured via deployment config"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/SemanticMatcher.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-094 — pgvector integration + HNSW indexes
```yaml
id: TASK-094
title: pgvector integration + HNSW indexes
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-049
sprint: SPRINT-08+
dependencies: [TASK-093]
estimate: L
acceptance_criteria:
  - "vector columns populated; HNSW indexes per §7.2"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/, backend/src/main/resources/db/migration/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

### TASK-095 — Semantic matcher + fallback
```yaml
id: TASK-095
title: Semantic matcher + fallback
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-049
sprint: SPRINT-08+
dependencies: [TASK-094]
estimate: M
acceptance_criteria:
  - "semantic phase resolves ambiguous/unmatched requirements; exact phase still first"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/matching/SemanticMatcher.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-050 — Multiple Resume Variants

### TASK-096 — Variant model + UI
```yaml
id: TASK-096
title: Variant model + UI
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-050
sprint: SPRINT-08+
dependencies: []
estimate: L
acceptance_criteria:
  - "user maintains multiple master resumes/variants; analyses pick a variant"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/, frontend/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-051 — Cover Letters

### TASK-097 — Cover letter generation + export
```yaml
id: TASK-097
title: Cover letter generation + export
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-051
sprint: SPRINT-08+
dependencies: []
estimate: M
acceptance_criteria:
  - "generate a cover letter from the analysis; export PDF/DOCX"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/, backend/src/main/java/com/atsdoctor/backend/infrastructure/export/ExportService.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-052 — Application Tracking

### TASK-098 — Application tracker UI + data
```yaml
id: TASK-098
title: Application tracker UI + data
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-052
sprint: SPRINT-08+
dependencies: []
estimate: L
acceptance_criteria:
  - "track application status per job"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/persistence/, frontend/]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

## FEAT-053 — OCR for Scanned PDFs

### TASK-099 — Tesseract OCR optional integration
```yaml
id: TASK-099
title: Tesseract OCR optional integration
status: BACKLOG
priority: P2
epic: EPIC-012
feature: FEAT-053
sprint: SPRINT-08+
dependencies: []
estimate: M
acceptance_criteria:
  - "optional Tesseract path extracts image-only PDFs; feature-flagged"
files_or_modules: [backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/PdfParser.java]
tests: []
created_at: 2026-08-12
updated_at: 2026-08-12
```

---

## FEAT-054 — Export Template Selection

### TASK-100 — App-side export template catalog + master-resume editor parity
```yaml
id: TASK-100
title: App-side export template catalog + master-resume editor parity
status: DONE
priority: P1
epic: EPIC-009
feature: FEAT-054
sprint: SPRINT-07
dependencies: [TASK-082]
estimate: M
acceptance_criteria:
  - "resume-templates.yml catalog (slugs, app-only + column layout for PDF/DOCX); GET /resume-templates lists slug/name/description"
  - "PUT /tailored/{id}/template persists the slug; export accepts ?template= override (invalid slug -> 400)"
  - "ExportService resolves the template for HTML/PDF/DOCX; LaTeX export supports the column layout"
  - "frontend EditWorkspace genericized (Structured/LaTeX/JSON modes with onSave overrides) and reused by /resume master editor"
  - "Workspace toolbar gains the template selector; exports carry the selected template"
files_or_modules: [backend/src/main/resources/resume-templates.yml, backend/src/main/java/com/atsdoctor/backend/api/tailor/ResumeTemplateCatalog.java, backend/src/main/java/com/atsdoctor/backend/api/tailor/ResumeTemplateController.java, frontend/app/(dashboard)/tailored/[id]/EditWorkspace.tsx, frontend/app/(dashboard)/resume/page.tsx]
tests: [ResumeTemplateCatalogTest, ResumeTemplateControllerTest, TailoringExportControllerTest, TailoringReviewFlowTest]
created_at: 2026-08-15
updated_at: 2026-08-15
```

---

## Summary

- Tasks: **100** (TASK-001..TASK-100)
- By status: 84 DONE · 0 IN_PROGRESS · 9 READY · 0 PLANNED · 7 BACKLOG
- Every task maps to a feature and a sprint.
