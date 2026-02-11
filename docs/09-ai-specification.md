# 09 — AI Specification

AI abstraction layer per PRD v1.2 §6. ATS Doctor is **AI-provider/model
agnostic**. Application and domain logic never calls a provider directly — it
submits **tasks** through the AI abstraction layer. OmniRoute is the gateway
**below** this layer, not the top of it.

```text
ATS Doctor
    │
    v
AIService / AI Abstraction Layer      ← application/ai/AIService.java
    │
    v
Task Router / Model Profile           ← application/ai/TaskRouter.java, ModelProfile.java
    │
    v
Spring AI (ChatClient)                ← infrastructure/ai/SpringAiChatGateway.java
    │
    v
OmniRoute                             ← omniroute/config.yaml (deployment-only)
    │
    +── DeepSeek
    +── Gemini
    +── Claude
    +── OpenAI
    +── Ollama / Local Models
    +── Other future providers
```

**Status note**: the AI layer is implemented on **Spring AI** in Sprint 0
(TASK-012..018 — all `DONE`, verified by unit tests and the boot smoke check).
DeepSeek is the default deployment configuration only — it never appears in
application logic (DEC-005).

---

## 1. Architecture components

| Component | Module | Responsibility | Status |
|-----------|--------|----------------|--------|
| `AIService` | `application/ai/AIService.java` | Stable interface: submit tasks, validate output, select profile, retries, timeouts, record `ai_runs`, return normalized results. | DONE (TASK-015, TASK-016) |
| Task Router | `application/ai/TaskRouter.java` | Maps application tasks → profiles via `ai-tasks.yml`. Guards deterministic tasks. | DONE (TASK-013) |
| Model Profiles | `application/ai/ModelProfile.java` | Profile constants (`cheap`/`quality`/`local`/`private`) + `PROFILE_<name>` env overrides. | DONE (TASK-012) |
| Prompt templates | `resources/prompts/*.txt` + `application/ai/PromptService.java` | Versioned prompt templates + `render(...)`. | DONE (TASK-014) |
| Chat Gateway | `infrastructure/ai/SpringAiChatGateway.java` | Spring AI `ChatClient`; retry+backoff, one fallback, dev-stub degradation, recorder hook. | DONE (TASK-016) |
| OmniRoute config | `omniroute/config.yaml` | Deployment-level profile→model mapping. | DONE (TASK-018) |

Business logic calls tasks, never providers (PRD §6):

```java
// The ONLY pattern allowed:
AiResult result = aiService.generate(new AiRequest(AiTask.RESUME_PARSER, input));

// Forbidden — couples the application to a vendor:
// deepseek.generate(...) / gemini.generate(...) / claude.generate(...)
```

## 2. Task Router & Profiles

```yaml
# ai-tasks.yml  (application-level task → profile mapping)
ai_tasks:
  resume_parser:          { profile: cheap }
  jd_parser:              { profile: cheap }
  requirement_extraction: { profile: cheap }
  resume_tailoring:       { profile: quality }
  fact_validation:        { profile: quality }

# profiles.yaml  (deployment-level profile → model mapping)
profiles:
  cheap:   { provider: omniroute, model: configurable }
  quality: { provider: omniroute, model: configurable }
  local:   { provider: omniroute, model: configurable }
  private: { provider: omniroute, model: configurable }
```

Exact model names live **only** in deployment/configuration, never in
application/domain code.

### Default deployment (PRD §6.3)

```text
cheap   → DeepSeek V4 Flash   (deepseek-v4-flash)
quality → DeepSeek V4 Flash   (deepseek-v4-flash)
local   → Ollama text model   (llama3.2:latest — text/instruction, NOT llava)
```

Same deployment can remap later without application changes (e.g.,
`quality → Claude`). All model names stay configurable in `omniroute/config.yaml`.

### Model fallback (MVP-simple, PRD §6.4)

```text
Task → Primary model → failure? → Fallback model → failure? → controlled error
```

- At most **one** fallback per task (e.g., `quality → local`).
- Every failure and fallback is recorded in `ai_runs`.
- Dev mode degrades to a labeled `stub` provider when OmniRoute is unreachable.

## 3. AI Task Table (PRD §6.6)

| Task | AI Required | Default Profile | Implementation |
|------|-------------|-----------------|----------------|
| Resume text extraction | No | N/A | Deterministic (Apache PDFBox / Apache POI) |
| Resume structuring | Yes | cheap | LLM |
| JD structuring | Yes | cheap | LLM |
| Requirement extraction | Yes | cheap | LLM |
| Exact matching | No | N/A | Deterministic |
| Semantic matching | Optional (v0.3) | local/embedding | LLM/embeddings |
| Job Match Score | No | deterministic | Deterministic |
| Evidence selection | Mostly deterministic | N/A | Hybrid |
| Resume tailoring | Yes | quality | LLM |
| Fact validation | Hybrid | quality | Hybrid (deterministic + LLM) |
| PDF generation | No | N/A | Deterministic (Flying Saucer + OpenPDF) |
| DOCX generation | No | N/A | Deterministic (Apache POI) |

## 4. Per-task contract

### 4.1 `resume_parser` (prompt `resume-parser-v1`)
- **In**: `resume_text` (raw extracted text).
- **Out**: JSON — `basics` (name, email, phone, location, linkedin, github), `summary`, `skills[]`, `experience[]` (company, title, start YYYY-MM, end YYYY-MM or "present", bullets[]), `projects[]` (name, description, technologies), `education[]` (institution, degree, field, start, end).
- **Rules**: output ONLY valid JSON; do not invent information.
- **Validation**: §4.2 Java record + Bean Validation (TASK-032).
- **Profile**: cheap. **Fallback**: none in MVP (via facade default). **Records**: `ai_runs`.

### 4.2 `jd_parser` (prompt `jd-parser-v1`)
- **In**: `jd_text`.
- **Out**: JSON — `job` (title, company, location, seniority), `requirements[]` (text, type, importance), `skills` (required, preferred, nice_to_have), `responsibilities[]`, `keywords[]`.
- **Rules**: output ONLY valid JSON.
- **Validation**: §4.3 Java record + Bean Validation (TASK-042).
- **Profile**: cheap.

### 4.3 `requirement_extraction` (prompt `requirement-extraction-v1`)
- **In**: structured JD.
- **Out**: requirement rows → `job_requirements` (text, type, importance, keywords).
- **Profile**: cheap.

### 4.4 `resume_tailoring` (prompt `tailor-bullet-v1`)
- **In**: `jd_requirements`, `resume_evidence`, `original_bullet`.
- **Out**: rewritten bullet.
- **Rules**: A (facts) — do NOT add/change technologies, companies, metrics, titles, dates; B (descriptors) — only when evidence supports; C (claims) — never introduce new achievements; only original evidence; preserve meaning, improve keyword alignment; professional + concise.
- **Profile**: quality. Runs per-bullet through the decision layer (FEAT-028/029).

### 4.5 `fact_validation` (prompt `validator-v1`)
- **In**: `original_resume`, `tailored_resume`.
- **Out**: JSON — `is_valid: boolean`, `issues[]` (`type ∈ {unsupported_technology, inflated_metric, new_company, new_title, unsupported_achievement}`, `text`, `original_evidence`, `suggestion`).
- **Rules**: A — facts must exist in original; B — descriptors must be evidence-supported; C — no new achievements/claims.
- **Hybrid**: deterministic checks (A/B) + AI (PRD §5.5). **Profile**: quality.

### 4.6 `embedding` (v0.3+, deferred)
- Semantic embeddings for `resume_evidence` and `job_requirements` (pgvector).
- **Profile**: local. Not in MVP (DEC-015).

## 5. Prompt versioning (PRD §6.5)

| Task | Prompt template | Notes |
|------|-----------------|-------|
| Resume structuring | `resume-parser-v1` | Bump version when output fields change |
| JD structuring | `jd-parser-v1` | |
| Requirement extraction | `requirement-extraction-v1` | |
| Resume tailoring | `tailor-bullet-v1` | Per-bullet |
| Fact validation | `validator-v1` | Category A/B/C checks |

Every AI output records: `model`, `provider`, `prompt_version`, `configuration`.

## 6. Execution tracking (`ai_runs`)

Every AI call records: `task`, `provider`, `model`, `model_version`, `profile`,
`prompt_version`, `input_hash`, `output`, `status`, `error`, `latency_ms`,
`input_tokens`, `output_tokens`, `created_at` (schema in `08-database-schema.md`
§1.9). Do not store sensitive raw prompts — `input_hash` + metadata suffices.

## 7. AI configuration endpoints

`GET /ai/config`, `PUT /ai/config`, `GET /ai/models` (see `07-api-contract.md`
§6, FEAT-009). The transparency payload shows provider + "data leaves this
machine: yes/no" (PRD §12.2).
