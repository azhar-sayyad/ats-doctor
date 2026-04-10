# 07 — API Contract

Backend API per PRD v1.2 §8. The API models the **pipeline**, not generic
CRUD — the **analysis** is the central object:

```text
Resume
   │
   ├──────────────┐
   │              │
   ▼              ▼
Analysis ←────── Job
   │
   ▼
Tailored Resume
   │
   ├── Changes
   ├── Validation
   └── Exports
```

**Base URL:** `http://localhost:8000/api/v1`

Format: JSON unless noted. JSON field names are **snake_case** (Spring
Jackson `SNAKE_CASE` naming strategy) to match the payload names below. Errors
follow **Spring `ProblemDetail` (RFC 7807)** — `application/problem+json` with
`status`/`title`/`detail` (PRD §8). Statuses reference the state machines in
`08-database-schema.md` and PRD §5.8.

**List pagination**: the list endpoints (`GET /jobs`, `GET /analyses`,
`GET /tailored`) accept optional `page` (0-based, default `0`) and `size`
(default `10`, clamped to `[1, 1000]`) query params and return a page
envelope instead of a bare array:

```json
{"items": [ ...newest first, at most `size` rows... ],
 "page": 0, "size": 10, "total": 27, "has_more": true}
```

> Status truth lives in `05-backlog.md`. Feature mappings below reference
> `03-features.md`; tasks reference `06-technical-tasks.md`.

---

## 1. Resume Endpoints

| Endpoint | Method | Purpose | Request | Response | Status | Feature |
|----------|--------|---------|---------|----------|--------|---------|
| `/resumes/upload` | POST | Upload resume file. | multipart `file: UploadFile` (PDF/DOCX/TXT, ≤10MB) | `resume_version` JSON | `UPLOADED` → `EXTRACTING` → `PARSING` → `READY`/`FAILED` | FEAT-010 (TASK-026/027/028) |
| `/resumes/current` | GET | Get latest master resume version. | — | `resume_version` JSON | — | FEAT-015 |
| `/resumes/versions` | GET | List all resume versions. | — | `[resume_version]` JSON | — | FEAT-045 |
| `/resumes/{version_id}` | GET | Get specific resume version. | path `version_id: UUID` | `resume_version` JSON | 404 if not found | FEAT-015 |
| `/resumes/{version_id}/edit` | PUT | Update structured resume. | path `version_id: UUID`; body `structured_data: JSON` (§4.2) | `resume_version` JSON | validated against §4.2 schema | FEAT-016 (TASK-038) |

Security (PRD §12.1): only PDF/DOCX/TXT; ≤10MB per file; sanitize uploaded
files; path-traversal-safe storage under `data/resumes/`.

## 2. Job Endpoints

| Endpoint | Method | Purpose | Request | Response | Status | Feature |
|----------|--------|---------|---------|----------|--------|---------|
| `/jobs` | POST | Upload JD file or paste text. | multipart `file: UploadFile` **or** `text: str` | `job` JSON | `CREATED` → `EXTRACTING` → `PARSING` → `READY`/`FAILED` | FEAT-017 (TASK-039) |
| `/jobs` | GET | List jobs (paginated, newest first). | query `page: int` (default 0), `size: int` (default 10) | page envelope `{"items": [job], "page", "size", "total", "has_more"}` | — | FEAT-021 |
| `/jobs/{job_id}` | GET | Get specific job. | path `job_id: UUID` | `job` JSON | 404 if not found | FEAT-021 |
| `/jobs/{job_id}` | DELETE | Delete job. | path `job_id: UUID` | `{"status": "deleted"}` | 404 if not found | FEAT-021 |

## 3. Analysis Endpoints

| Endpoint | Method | Purpose | Request | Response | Status | Feature |
|----------|--------|---------|---------|----------|--------|---------|
| `/analyses` | POST | Analyze job against resume (queues pipeline). | body `job_id: UUID`, `resume_version_id: UUID` | `analysis` JSON (state `QUEUED`) | `QUEUED` → `MATCHING` → `SCORING` → `READY`/`FAILED`; 400 if job/resume missing or not READY | FEAT-027 (TASK-056) |
| `/analyses` | GET | List analyses (paginated, newest first). | query `page: int` (default 0), `size: int` (default 10) | page envelope `{"items": [analysis], "page", "size", "total", "has_more"}` | — | FEAT-027 (TASK-058) |
| `/analyses/{analysis_id}` | GET | Get analysis results (incl. status). | path `analysis_id: UUID` | `analysis` JSON | includes `score`, `score_breakdown` (6 categories + `total`), `matches`, `gaps`, `generation`; 404 if not found | FEAT-027 (TASK-058) |
| `/analyses/{analysis_id}/reanalyze` | POST | Re-run analysis. | path `analysis_id: UUID` | `analysis` JSON (state `QUEUED`) | `READY`/`FAILED` → `QUEUED`; 409 while running; 404 if not found | FEAT-027 (TASK-058) |
| `/analyses/{analysis_id}/tailor` | POST | Generate tailored resume from analysis. | path `analysis_id: UUID` | `tailored_resume` JSON | Tailoring `QUEUED` → `GENERATING` → `VALIDATING` → `READY`; 400/404/409 ProblemDetail; failures surface via `error` | FEAT-032 (TASK-066) |

## 4. Tailoring Endpoints

| Endpoint | Method | Purpose | Request | Response | Status | Feature |
|----------|--------|---------|---------|----------|--------|---------|
| `/tailored` | GET | List tailored resumes (paginated, newest first). | query `page: int` (default 0), `size: int` (default 10) | page envelope `{"items": [tailored_resume], "page", "size", "total", "has_more"}` | — | FEAT-032 (TASK-066) |
| `/tailored/{tailored_id}` | GET | Get tailored resume. | path `tailored_id: UUID` | `tailored_resume` JSON | includes `content` (summary/experience/order/generation), `score_before`, `score_after`, `state`, `template`, `error`; 404 if not found | FEAT-032 (TASK-066) |
| `/tailored/{tailored_id}/edit` | PUT | Persist a full edited document (structured JSON). | path `tailored_id: UUID`; body `structured_data: JSON` (§4.2) | `tailored_resume` JSON (returns the edited `document` — viewer/exports/validation switch to it) | 400 invalid document; 404 unknown id | FEAT-041 (TASK-080) |
| `/tailored/{tailored_id}/changes` | GET | List per-change review state. | path `tailored_id: UUID` | `[tailored_change]` JSON | — | FEAT-037 (TASK-074) |
| `/tailored/{tailored_id}/changes/{change_id}` | POST | Accept/reject/edit/regenerate a change. | path ids; body `{"action": "accept" \| "reject" \| "edit" \| "regenerate", "new_text": "…"}` (edit requires non-blank `new_text`) | `tailored_change` JSON | change → `ACCEPTED`/`REJECTED`/`EDITED`/`REGENERATED`; 400 unknown action; 404 unknown ids; 409 when not reviewable | FEAT-037 (TASK-075) |
| `/tailored/{tailored_id}/changes/{change_id}/trace` | GET | Resolve a change to its source chain. | path ids | trace JSON: change + `evidence` (text/section/section_id/`source_refs`) + `section` (company/title) + `bullet` + `validation_issues` | 404 unknown tailored/change | FEAT-036 (TASK-072) |
| `/tailored/{tailored_id}/validate` | POST | Run fact-grounding check. | path `tailored_id: UUID` | `validation` JSON (issues: `unsupported_technology \| inflated_metric \| new_company \| new_title \| unsupported_achievement`) | `VALIDATING`; 409 unless state ∈ VALIDATING/READY/NEEDS_REVIEW | FEAT-035 (TASK-071) |
| `/tailored/{tailored_id}/approve` | POST | Approve all changes (required before export). | path `tailored_id: UUID` | `{"status": "approved"}` | Tailoring → `APPROVED`; 409 while any change is `PENDING` or the persisted validation report is not `valid=true` (idempotent once `APPROVED`) | FEAT-039 (TASK-078) |
| `/tailored/{tailored_id}/template` | PUT | Set the export template. | path `tailored_id: UUID`; body `{"template": "<slug>"}` | `tailored_resume` JSON | 400 unknown slug (rejected against catalog §6.6); 404 unknown id | FEAT-054 (TASK-100) |

Per-change review lifecycle: `PENDING` → `ACCEPTED | REJECTED | EDITED | REGENERATED`.

## 5. Export Endpoints

| Endpoint | Method | Purpose | Request | Response | Feature |
|----------|--------|---------|---------|----------|---------|
| `GET /tailored/{tailored_id}/export/{format}` | GET | Export the finalized resume. | path `tailored_id: UUID`, `format` ∈ `pdf` \| `docx` \| `json`; query `template: str` (optional — overrides the persisted selection) | PDF/DOCX file (Content-Disposition attachment) or JSON | FEAT-054 (TASK-100) |

> **Export gate** (PRD §8.2): export returns **409 Conflict** until the state is
> `READY`/`NEEDS_REVIEW`/`APPROVED`, all changes are resolved (no `PENDING`
> rows) and the persisted validation report is `valid=true` (`pdf_path`/
> `docx_path`/`html` are populated during export; 400 for an unknown `format`
> or `template`; 404 for an unknown `tailored_id`).

## 6. Resume Template Catalog

| Endpoint | Method | Purpose | Request | Response | Feature |
|----------|--------|---------|---------|----------|---------|
| `/resume-templates` | GET | List export templates (app-side catalog, NOT AI-generated). | — | `[{"slug", "name", "description"}]` JSON | FEAT-054 (TASK-100) |

Templates are declared in `backend/src/main/resources/resume-templates.yml`
(no database table). The default template slug is `ats_clean`; templates are
referenced by slug from the export endpoint and stored on
`tailored_resumes.template`. Skipped/REDACTED text per selected template — only
the **original** fallback text is shown in the HTML preview and exported.

## 7. AI Configuration Endpoints

| Endpoint | Method | Purpose | Request | Response | Feature |
|----------|--------|---------|---------|----------|---------|
| `/ai/models` | GET | List available models via OmniRoute. | — | `[model]` JSON | FEAT-009 (TASK-024) |
| `/ai/config` | GET | Get current AI config. | — | `config` JSON | FEAT-009 (TASK-024) |
| `/ai/config` | PUT | Update AI model assignments. | body `config: JSON` | `config` JSON | FEAT-009 (TASK-024) |

`config` JSON exposes the profile→model mapping (§6.2/§6.3) plus transparency
payload (provider + "data leaves this machine: yes/no", PRD §12.2).

## 8. Cross-cutting

- **State-driven responses**: `analysis.state` (PRD names it `status`; V2/V3
  precedent — see 08-database-schema), `tailored_resume.status`,
  `resume_version` states are returned by the API and drive the frontend
  (PRD §5.8).
- **Traceability**: every tailored claim references `source_refs` (evidence IDs)
  — returned with tailored content for the "Why is this claim here?" UI (PRD §5.9).
- **ai_runs**: every AI task call is recorded server-side (PRD §6.7, §7.9).
- **Task mapping**: `ai_runs.task ∈ {resume_parser, jd_parser, requirement_extraction, resume_tailoring, fact_validation, embedding}`.
