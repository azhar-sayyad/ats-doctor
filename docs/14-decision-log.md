# 14 — Decision Log

Architectural and product decisions (DEC-###). DEC-001..015 derive from the
PRD v1.1 Change Log (recorded so they are traceable here); DEC-016..022 record
decisions made in this documentation system; **DEC-023+ finalize the v1.2
technology stack (Java 21 / Spring Boot)** and supersede the Python module
paths named in DEC-002..009, 016, 018.

Format: date, context, options, chosen, reason, impact. Superseded decisions
are marked, never rewritten.

---

| ID | Date | Decision | Chosen | Context | Reason | Impact | Status |
|----|------|----------|--------|---------|--------|--------|--------|
| DEC-001 | 2026-08-12 | "100% Local" → **Local-first** | Local storage default; AI inference may use a configured external provider | v1.0 claimed 100% local while allowing external AI APIs — inconsistent | Consistency; honest privacy disclosure | UI transparency payload (§12.2) | Active |
| DEC-002 | 2026-08-12 | **AI Abstraction Layer**: AIService → Task Router → Model Profiles, OmniRoute below it | App logic calls `ai.generate(task=...)`, never a vendor API | Provider-agnosticism | Complete provider/model independence | `core/ai/*` modules; forbidden vendor calls | Active |
| DEC-003 | 2026-08-12 | Define **AIService** interface (submit, validate, select profile, retries, timeouts, metadata, normalized results) | Single stable seam for business-logic AI calls | Consistency | One seam for all AI calls | `core/ai/aiservice.py` | Active |
| DEC-004 | 2026-08-12 | **Task Router + Model Profiles** (`cheap`/`quality`/`local`/`private`) | Decouple task mapping from provider/model names | Provider-agnosticism | Task→profile in `ai_tasks.yaml`; profile→model only in deployment | `core/ai/router.py`, `profiles.py` | Active |
| DEC-005 | 2026-08-12 | DeepSeek V4 Flash as **default model configuration**, not a dependency | Default deployment mapping; swappable (→Claude) without app changes | v1.1 clarifies §6.3 | DeepSeek never appears in application logic | `omniroute/config.yaml` only | Active |
| DEC-006 | 2026-08-12 | **Model fallback** — at most one fallback per task, controlled error | Reliability + provider independence, MVP-simple | Failure handling | Facade retry/fallback (`services/ai_service.py`) | Active |
| DEC-007 | 2026-08-12 | **Prompt versioning** (`resume-parser-v1`, `jd-parser-v1`, `tailor-bullet-v1`, `validator-v1`, ...) | Reproducibility | Track which prompt generated which output | `prompts.py` + recorded in `ai_runs` | Active |
| DEC-008 | 2026-08-12 | AI task table → **Task / AI Required / Default Profile** model | Deterministic vs. AI tasks + profile mapping explicit | Clarity | §6.6 | Active |
| DEC-009 | 2026-08-12 | Expand **`ai_runs`** (provider, model_version, profile, error, input/output tokens); don't store raw prompts | Auditability/debugging; input_hash only | Privacy + debugging | `ai_runs` table | Active |
| DEC-010 | 2026-08-12 | "Hallucination-Proof" → **Fact-Grounded**; defense-in-depth chain | Evidence model → allowed facts → LLM → deterministic → AI → human | No LLM validator can guarantee zero hallucinations | Validator architecture (SPRINT-05) | Active |
| DEC-011 | 2026-08-12 | **Resume Evidence Graph → Resume Evidence Model** (relational + JSONB, no graph DB) | Avoid unnecessary graph database | Simplicity | `resume_evidence` table | Active |
| DEC-012 | 2026-08-12 | Claim categories **A (immutable facts) / B (supported descriptors) / C (unsupported claims)** | Drive tailoring + validation | Not every new word is a hallucination | Claim categories in evidence/changes | Active |
| DEC-013 | 2026-08-12 | "ATS Score" → **"Job Match Score"** with explicit disclaimer | Alignment estimate, not an ATS score | Cannot know proprietary ATS algorithms | UI disclaimer everywhere a score is shown | Active |
| DEC-014 | 2026-08-12 | **Semantic matching / embeddings / pgvector → optional, post-MVP (Sprint 8+)** | Exact-first matching deterministic + debuggable for MVP | MVP scope | FEAT-049 BACKLOG | Active |
| DEC-015 | 2026-08-12 | Local model: **text/instruction model** (not `llava`); configurable | Correct default for text resume/JD work | `llava:13b` is a vision model — wrong default | `omniroute/config.yaml` | Active |
| DEC-016 | 2026-08-12 | **Processing state machines** (Resume/Job/Analysis/Tailoring), incl. `FAILED` on Job | Deterministic frontend/backend behavior | §5.8 | `core/states.py` (TASK-020) | Active |
| DEC-017 | 2026-08-12 | **Master Resume Source-of-Truth rule** + **Claim Traceability** (every claim → source evidence IDs) | Prevent resume drift; core MVP feature | §5.9 | Traceability UI + source_refs | Active |
| DEC-018 | 2026-08-12 | **Selective Tailoring** decision layer (rewrite only when needed) | Fewer AI calls, lower hallucination risk, fewer unnecessary changes | §5.4 | `tailor/decide.py` (FEAT-028) | Active |
| DEC-019 | 2026-08-12 | Roadmap reorganized into **Sprint 0–8+** (Foundation → Semantic matching) | Incremental; MVP not expanded | §10 | `04-sprints.md`, `12-release-plan.md` | Active |
| DEC-020 | 2026-08-12 | Documentation system: **living docs in `docs/`**, PRD v1.2 as single source of truth, referenced not copied | Status-updateable implementation docs | Reflect reality; no fabricated DONE | `README.md` + docs 01–15 | Active |
| DEC-021 | 2026-08-12 | Statuses: exact vocabulary + stable IDs (EPIC/FEAT/TASK/SPRINT/DEC/CL); status truth in `05-backlog.md` | Never reuse/renumber IDs; dashboard derived | Maintainability | README conventions | Active |
| DEC-022 | 2026-08-12 | Existing Sprint 0 code = **IN_PROGRESS, unverified** (no tests, backend does not boot) | Honest reflection of reality | No fabricated DONE | `13-project-status.md`, `04-sprints.md` | Superseded by CL-005 |
| DEC-023 | 2026-08-12 | **Final tech stack: Java 21 + Spring Boot modular monolith** (Spring Web, Spring Validation, Spring Data JPA + Hibernate). **Supersedes the Python module paths named in DEC-002..009, 016, 018** — those architectural decisions remain active, mapped to Java equivalents (e.g., `core/ai/aiservice.py` → `application/ai/AIService.java`, `core/states.py` → `domain/states/`) | Finalized technology decisions; explicitly **no microservices** (resume/job/ai/matching are domain modules, not apps) | One Spring Boot app with clear domain modules | `backend/src/main/java/com/atsdoctor/backend/` | Active |
| DEC-024 | 2026-08-12 | **Spring AI** as the model-agnostic chat client between AIService/TaskRouter and OmniRoute | `spring.ai.openai.base-url=http://omniroute:8080`; model names in config only | OmniRoute stays the gateway below Spring AI | Provider switching is deployment-only | `infrastructure/ai/SpringAiChatGateway`, `StubAiProvider` | Active |
| DEC-025 | 2026-08-12 | Backend build tool: **Maven** | Maven 3.9.x (`backend/pom.xml`) | Already installed locally; Spring Boot standard | Simple, reproducible CI/Docker builds | `backend/pom.xml`, `.github/workflows/ci.yml` | Active |
| DEC-026 | 2026-08-12 | Backend port remains **8000** (Spring `server.port=8000`) | `http://localhost:8000/api/v1` | API contract and compose already target 8000 | No client/contract churn | `application.yml`, `docker-compose.yml` | Active |
| DEC-027 | 2026-08-12 | Document processing: **Apache PDFBox** (PDF) + **Apache POI XWPF** (DOCX) | PDFBox + POI | Final decisions | Deterministic text extraction | `infrastructure/parsing/` | Active |
| DEC-028 | 2026-08-12 | Export stack: **HTML/CSS (Thymeleaf) → PDF via Flying Saucer + OpenPDF**; DOCX via **Apache POI** | Flying Saucer + OpenPDF + POI | Matches "HTML/CSS based ATS-friendly rendering → PDF export" | Reuses one HTML template for HTML/PDF | `infrastructure/export/`, `resources/templates/` | Active |
| DEC-029 | 2026-08-12 | **Flyway** for versioned PostgreSQL migrations | Flyway | Production-ready schema evolution | Versioned, deterministic migrations | `resources/db/migration/` | Active |
| DEC-030 | 2026-08-12 | Local file storage at `/data` via Docker volume, **backend-only mount** | `./data:/data` | Local-first; single filesystem accessor | Path-safe storage; no frontend FS access | `docker-compose.yml`, `infrastructure/files/` | Active |
| DEC-031 | 2026-08-12 | **pgvector / embeddings stay optional** — not MVP-mandatory | Deferred to v0.3+ (FEAT-049) | Exact-first matching suffices for the MVP | No pgvector dependency in the MVP build | FEAT-049, `08-database-schema.md` | Active |
| DEC-032 | 2026-08-12 | Test stack: **JUnit 5 + Mockito + AssertJ + Testcontainers** (PostgreSQL); Checkstyle via `mvn verify` | JUnit 5 + Testcontainers | Java ecosystem; same §13 success-metric gates | Integration tests run against real Postgres | `backend/src/test/java/` | Active |
| DEC-033 | 2026-08-12 | Repository initialized as a **monorepo** (frontend + backend + compose at root) | `ATSDoctor/` root | One repo, one compose, local-first deployment | Single versioned codebase | `README.md`, `docker-compose.yml` | Active |
