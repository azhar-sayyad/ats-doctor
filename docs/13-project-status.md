# 13 — Project Status (Dashboard)

**Derived from `05-backlog.md`. Do not edit the numbers here by hand** — update
`05-backlog.md` / `06-technical-tasks.md` and regenerate this summary (README
"How to update status").

Last generated: **2026-08-13**.

---

## 1. Overall

| Dimension | Total | DONE | IN_PROGRESS | PLANNED | READY | BACKLOG | BLOCKED |
|-----------|-------|------|-------------|---------|-------|---------|---------|
| Epics | 12 | 9 | 0 | 0 | 2 | 1 | 0 |
| Features | 53 | 43 | 0 | 0 | 5 | 5 | 0 |
| Tasks | 99 | 83 | 0 | 0 | 9 | 7 | 0 |

**SPRINT-00..06 (EPIC-001..009) are complete**: all feature/task rows of
SPRINT-00 (9/25), SPRINT-01 (7/13), SPRINT-02 (5/7), SPRINT-03 (6/13),
SPRINT-04 (5/8), SPRINT-05 (4/7) and SPRINT-06 (7/9) are DONE, verified by 248/248
backend tests (incl. seven Testcontainers full-pipeline tests) and a green
frontend build.

**SPRINT-05 (EPIC-007 — Fact Validation + Claim Traceability) completed
2026-08-13**: three-stage validation runs for real — deterministic validator
(A/B + C-claim achievement), `fact_validation` AI wiring (`validator-v1`),
token-identity output normalization, `ValidationRules` typed rule engine
(`unsupported_fact | unsupported_descriptor | unsupported_claim`); the pipeline
executes `VALIDATING` for real; `POST /tailored/{id}/validate` persists the
report; `GET /tailored/{id}/changes` + `trace` resolve every claim to its
evidence chain; trace UI under `/tailored`.

**SPRINT-06 (EPIC-008/009 — Review + PDF/DOCX Export) completed 2026-08-13**:
per-change lifecycle PENDING → ACCEPTED|REJECTED|EDITED|REGENERATED with
edit/regenerate re-validation, approval gate → `APPROVED` (409 on unresolved
changes or invalid reports), Thymeleaf HTML rendering, Flying Saucer + OpenPDF
PDF, Apache POI XWPF DOCX, `export/pdf|docx|json` endpoints with 409 export
gating; review UI (`ReviewPanel`) with before/after edit, approve + export
links under `/tailored/[id]`. Locked deviation recorded: edit is also allowed
on EDITED/REGENERATED rows (re-edit path avoids the re-generation deadlock) and
REJECTED rows are skipped by validation and export their original text.

## 2. Sprint status

| Sprint | Status | Notes |
|--------|--------|-------|
| SPRINT-00 Foundation | **DONE** | All 9 features / 25 tasks DONE — built, tested, smoke-checked (CL-008) |
| SPRINT-01 Master Resume | **DONE** | All 7 features / 13 tasks DONE — upload→parse→evidence→review pipeline (CL-009) |
| SPRINT-02 JD Intelligence | **DONE** | All 5 features / 7 tasks DONE — paste/upload JD→§4.3 structure→requirements→persist (CL-010) |
| SPRINT-03 Deterministic Matching + Job Match Score | **DONE** | All 6 features / 13 tasks DONE — matching + scoring + `/analyses` pipeline (CL-011) |
| SPRINT-04 AI Tailoring | **DONE** | All 5 features / 8 tasks DONE — TailorDecider + AI bullet/summary rewrites + V5 persistence + tailor API (CL-013) |
| SPRINT-05 Fact Validation + Claim Traceability | **DONE** | All 4 features / 7 tasks DONE — real `VALIDATING`, typed rule engine, validate + changes + trace APIs, trace UI (CL-015) |
| SPRINT-06 Review + PDF/DOCX Export | **DONE** | All 7 features / 9 tasks DONE — per-change review lifecycle, approval gate, HTML/PDF/DOCX export + gating, review UI (CL-017) |
| SPRINT-07 History + Versioning + Hardening | **ACTIVE** | Started 2026-08-13 (→ 08-15, capacity M) — EPIC-010/011, FEAT-044..048 + TASK-083..092 READY; TASK-087 (CI gate) landed first and is DONE (CL-018); FEAT-054 + TASK-100 (export template selection, editor parity) also DONE (CL-020) |
| SPRINT-08+ | PLANNED | BACKLOG items only |

## 3. In progress

SPRINT-07 (History + Versioning + Hardening, EPIC-010/011) — opened 2026-08-13;
implementation started with **TASK-087 (CI gate) DONE** — `mvn verify` +
JaCoCo check live (parser 93%, matcher 99%, tailor 94%, validator 100% vs
§13.1 targets 90/90/80/100), `api.version` baked into the surefire argLine,
`.github/workflows/ci.yml` shipped, **248/248** tests green; then
TASK-083/084 (version increment + immutable versions, source-of-truth
enforcement), TASK-085/086 (analysis history endpoints + UI), TASK-088
(integration test fixtures), TASK-089/090 (error handling + UX polish),
TASK-091/092 (security checklist §12.1 + AI transparency UI). Close = CL-019.

## 4. Blockers

None. (OmniRoute is external/fictional — the facade must degrade to stub when
unreachable; this is a designed behavior, not a blocker.)

Note: local Testcontainers runs need only
`DOCKER_HOST=unix:///Users/azhar/.orbstack/run/docker.sock` — the surefire
argLine now bakes in `-Dapi.version=1.41` (OrbStack requires API ≥1.40;
`/var/run/docker.sock` is a stale symlink).

## 5. Next up

SPRINT-07 (History + Versioning + Hardening, EPIC-010/011) — active; phase
plan in `04-sprints.md`: TASK-083/084 (immutable versions + source-of-truth) →
TASK-085/086 (analysis history) → TASK-088 (fixtures) →
TASK-089/090 (error handling + UX) → TASK-091/092 (security + AI
transparency) → close as CL-019.

After that: SPRINT-08+ (EPIC-012 — Advanced: semantic matching + embeddings,
resume variants, cover letters, application tracking, OCR) is BACKLOG only.

## 6. Change summary

| Date | Change | Reference |
|------|--------|-----------|
| 2026-08-15 | **Master resume View/Edit split (UI)**: `/resume` gains View (preview-only dialog via `PreviewModal`) + Edit (dedicated `/resume/edit` page running the shared 3-mode editor) action cards; inline editor removed from the overview; `next build` + lint green (CL-021) | `15-change-log.md` CL-021 |
| 2026-08-15 | **Export template selection + master-resume editor parity**: `resume-templates.yml` catalog (five `ats_clean`-family templates — app-only + column layouts), `ResumeTemplateCatalog` + `GET /resume-templates`, `PUT /tailored/{id}/template` persists the slot, `ExportService` resolves the template for HTML/PDF/DOCX (+ `?template=` override; invalid slug → 400), Thymeleaf `resume.html`/`latex.tex` parameterized; frontend `EditWorkspace` genericized (Structured/LaTeX/JSON modes with `onSave` overrides) and reused by `/resume` master editor via `editMasterResume` (`PUT /resumes/{id}/edit`), Workspace toolbar gains the template selector and exports carry the selection; FEAT-054 + TASK-100 DONE, counts regenerated (9 EPICs / 44 FEATs / 84 TASKs DONE); **310/310 backend tests green**, `next build` + lint green | `15-change-log.md` CL-020 |
| 2026-08-13 | SPRINT-07 progress: **TASK-087 DONE** — `mvn verify` CI gate live (JaCoCo 0.8.13 + surefire argLine with baked-in `api.version`, check rules = §13.1 targets 90/90/80/100), coverage tests added (matcher 87.6→99.0%, validator 96.8→100%; parser 93.0%, tailor 94.0%), `.github/workflows/ci.yml` shipped, unreachable defensive branch removed from `DeterministicValidator.snippet` (blocked the 100% target); **248/248 tests green** | `backend/pom.xml`, `backend/src/{main,test}/java`, `.github/workflows/ci.yml`, `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `11-testing-strategy.md`, `13-project-status.md` |
| 2026-08-13 | **SPRINT-06 completed (EPIC-008/009 — Review + PDF/DOCX Export)**: per-change lifecycle PENDING → ACCEPTED\|REJECTED\|EDITED\|REGENERATED (`ChangeReviewService`, edit/regenerate re-validate), `POST /tailored/{id}/approve` → `APPROVED` (idempotent; 409 on unresolved changes/invalid report), Thymeleaf HTML + Flying Saucer/OpenPDF PDF + Apache POI XWPF DOCX via `ExportService` (`export/pdf\|docx\|json`, 409 export gate), `ReviewPanel` UI with approve + export links under `/tailored/[id]`; deviations recorded in `04-sprints.md` (edit allowed on EDITED/REGENERATED rows — re-edit path; REJECTED rows skipped by validation and exported as original text; Flying Saucer dependency relocated to `org.xhtmlrenderer:flying-saucer-pdf:9.4.0`); Testcontainers export test asserts PDF `%PDF` / DOCX `PK` magic + persisted `pdf_path`/`docx_path`; **237/237 backend tests green**, `next build` green; EPIC-008/009 + FEAT-037..043 + TASK-074..082 DONE, counts regenerated (9 EPICs / 43 FEATs / 82 TASKs DONE), SPRINT-06 CLOSED | `15-change-log.md` CL-017 |
| 2026-08-13 | **SPRINT-07 opened (EPIC-010/011 — History + Versioning + Hardening)**: yaml ACTIVE (2026-08-13 → 08-15, capacity M); phase plan set (TASK-087 CI gate → immutable versions TASK-083/084 → analysis history TASK-085/086 → fixtures TASK-088 → error handling/UX TASK-089/090 → security + AI transparency TASK-091/092 → close CL-019); FEAT-044..048 + TASK-083..092 READY, counts regenerated (9 EPICs / 43 FEATs / 82 TASKs DONE, 5 FEATs / 10 TASKs READY) | `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `13-project-status.md`, `15-change-log.md` |
| 2026-08-13 | **SPRINT-06 opened (EPIC-008/009 — Review + PDF/DOCX Export)**: yaml ACTIVE (2026-08-13 → 08-15, capacity M); head start note (SPRINT-05 shipped the change list + trace drawer under `/tailored`; TASK-074 GET side done — POST lifecycle remains); locked decisions (TailoringState gains APPROVED, still no FAILED; PENDING → ACCEPTED\|REJECTED\|EDITED\|REGENERATED via StateMachines; EDITED/REGENERATED re-validate before re-approval; export 409 until changes resolved + warnings cleared per §8.2; `html`/`pdf_path`/`docx_path` populated); phase plan A–G deepened (B: POST change actions + regenerate re-run + revalidation; C: review controls + before/after diff on the SPRINT-05 page; D: approve → APPROVED + 409 gate; E: Thymeleaf sanitized template; F: Flying Saucer + OpenPDF; G: POI XWPF + export endpoints + Testcontainers export test) — close becomes CL-017 (CL-016 supersedes the earlier "close as CL-016" wording); EPIC-008/009 IN_PROGRESS, FEAT-037..043 + TASK-074..082 READY, counts regenerated (7 EPICs / 36 FEATs / 73 TASKs DONE, 2 EPICs IN_PROGRESS, 9 TASKs READY) | `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `13-project-status.md`, `15-change-log.md` |
| 2026-08-13 | **SPRINT-05 (EPIC-007 — Fact Validation + Claim Traceability) implemented and verified**: deterministic validator (A/B + C-claim variant), `fact_validation`/`validator-v1` wiring (`AiValidator`, one `ai_runs` row per call), token-identity output normalization, `ValidationRules` typed verdicts; `VALIDATING` no longer a no-op — the pipeline validates for real and persists the report (`validation` JSONB, V6); `POST /tailored/{id}/validate` + `GET /tailored/{id}/changes` (read-only listing, GET side of TASK-074 pulled in from SPRINT-06) + `GET /tailored/{id}/changes/{id}/trace` (evidence chain + section context + bullet + its issues); frontend `/tailored` index + `[id]` page + "Why is this claim here?" `TraceDrawer`. **Two Testcontainers-found bugs fixed**: `evidence_id` now resolves the evidence row UUID from the bullet id (was NULL → empty traces) and validation `type` normalized to the §5.8 lowercase contract. 226/226 tests green incl. Testcontainers validate+traces round-trip; `next build` green; docs 04/05/06/07/13/15 reconciled. Close is CL-015 (CL-014 was taken by the planning row) | `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `13-project-status.md`, `15-change-log.md`, `backend/*`, `frontend/*` |
| 2026-08-13 | SPRINT-05 progress: TASK-067..070 implemented and green — deterministic validator (A/B + C-claim achievement variant), `fact_validation` AI wiring (`validator-v1`), validation output normalization (token-identity case-insensitive dedupe), `ValidationRules` rule engine (`Verdict`, merge + stable fact→descriptor→claim order); suite 204/204 green; no docker-compose build — E2E left to manual check | `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `13-project-status.md` |
| 2026-08-13 | SPRINT-04 started (EPIC-006 — AI Tailoring): phase plan A–G written in `04-sprints.md` (contracts → TailorDecider → bullet rewrite via `resume_tailoring` AI call → changes → summary → reorder → V5 persistence → `POST /analyses/{id}/tailor` API + E2E); EPIC-006 IN_PROGRESS, FEAT-028..032 + TASK-059..066 READY, counts regenerated (5 EPICs DONE / 1 IN_PROGRESS, 27 FEATs DONE / 5 READY, 58 TASKs DONE / 8 READY); stale EPICs count line in `05-backlog.md` fixed (was 3 DONE / 8 PLANNED — missed by CL-011) | `04-sprints.md`, `05-backlog.md`, `06-technical-tasks.md`, `13-project-status.md`, `15-change-log.md` |
| 2026-08-13 | SPRINT-03 completed: EPIC-004/005 + FEAT-022..027 + TASK-046..058 DONE; deterministic matching + weighted Job Match Score + `/analyses` pipeline (V4 migration, reanalyze, no AI calls in matching); 141/141 tests; docs 03/04/05/06/07/08/13/15 reconciled | `15-change-log.md` CL-011 |
| 2026-08-13 | SPRINT-03 started (EPIC-004/005): TASK-046 IN_PROGRESS, TASK-047..058 READY; backend-only scope, capacity M (13th–14th), deterministic exact-first matching + semantic stub hook confirmed | `04-sprints.md` |
| 2026-08-13 | SPRINT-02 completed: EPIC-003 + FEAT-017..021 + TASK-039..045 DONE; JD pipeline (paste/upload→extract→§4.3 structure→requirements→persist), V3 migration, async worker + two stubbed AI calls, list/get/delete; 95/95 tests; docs 03/04/05/06/08/13/15 reconciled | `15-change-log.md` CL-010 |
| 2026-08-13 | SPRINT-01 completed: EPIC-002 + FEAT-010..016 + TASK-026..038 DONE; resume pipeline (upload/extract/parse/evidence/persist/review/edit), V2 migration, async worker, stub §4.2 output, review UI; docs 03/04/05/06/07/08/13 reconciled; `.env` persistence enabled | `15-change-log.md` CL-009 |
| 2026-08-12 | SPRINT-00 completed: EPIC-001 + FEAT-001..009 + TASK-001..025 DONE; `.env.example` extended; docs 03/05/06/08/09/13 reconciled | `15-change-log.md` CL-008 |
| 2026-08-12 | PRD bumped to v1.2 — final stack Java 21 + Spring Boot; DEC-023..033 recorded | `15-change-log.md` CL-006 |
| 2026-08-12 | Docs 01–15 reconciled to the v1.2 stack (Java/Spring paths, Spring AI, Flyway, Testcontainers, ProblemDetail) | CL-007 |
| 2026-08-12 | Documentation set created (README + 01–15) | `15-change-log.md` CL-001..004 |
| 2026-08-12 | Reality correction: removed phantom "written scaffold" status; SPRINT-00 reset to greenfield | CL-005 |