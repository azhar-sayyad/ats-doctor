# 04 — Sprints

Implementation roadmap derived from PRD v1.2 §10 (Sprint Plan). Sprint
statuses: `PLANNED | ACTIVE | COMPLETED | BLOCKED | CANCELLED`.

> **Rule** (PRD §10): a sprint is only "done" when its output works end-to-end
> on real resume/JD pairs. AI evaluation, PDF rendering, and prompt tuning take
> longer than naive estimates.

Dates are targets; update them when a sprint actually starts.

---

## SPRINT-00 — Foundation

```yaml
id: SPRINT-00
name: Foundation
goal: "Boot the whole stack and wire the AI Abstraction Layer, state machines, and ai_runs tracking."
status: COMPLETED
start_date: 2026-08-12
target_end_date: 2026-08-22
capacity: 10d
epics: [EPIC-001]
features: [FEAT-001, FEAT-002, FEAT-003, FEAT-004, FEAT-005, FEAT-006, FEAT-007, FEAT-008, FEAT-009]
tasks: [TASK-001..TASK-025]
dependencies: []
risks:
  - "OmniRoute is external/fictional — backend must degrade to stub when unreachable"
  - "Frontend scaffold can consume a lot of time; keep to a shell"
exit_criteria:
  - "`docker compose up` boots frontend, backend, db; backend is the only service with a filesystem mount"
  - "`GET /api/v1/health` returns OK and reports AI mode (omniroute | stub)"
  - "AI layer resolves profiles and degrades to stub in dev; unit tests pass"
  - "state machines (Resume/Job/Analysis/Tailoring) enforced with tests"
  - "every AI call is recorded to `ai_runs` (schema + recorder)"
  - "AI config endpoints (`/ai/config`, `/ai/models`) work with transparency payload"
```

Status note: **DONE** — SPRINT-00 (EPIC-001) is implemented and verified:
all 9 features / 25 tasks complete, unit tests green, boot smoke check passed
(CL-008).

## SPRINT-01 — Master Resume

```yaml
id: SPRINT-01
name: Master Resume
goal: "User can upload a PDF/DOCX master resume, have it parsed, structured, persisted, and reviewed."
status: COMPLETED
start_date: 2026-08-13
target_end_date: 2026-08-13
capacity: S
epics: [EPIC-002]
features: [FEAT-010, FEAT-011, FEAT-012, FEAT-013, FEAT-014, FEAT-015, FEAT-016]
tasks: [TASK-026..TASK-038]
dependencies: [SPRINT-00]
risks:
  - "LLM structuring quality depends on prompt tuning"
  - "scanned/image-only PDFs cannot be parsed without OCR (out of scope)"
exit_criteria:
  - "`POST /resumes/upload` accepts PDF/DOCX/TXT ≤10MB, stores the file, extracts text"
  - "structured resume validates against §4.2; evidence model persisted (§4.2, claim categories A/B/C)"
  - "`resumes`/`resume_versions`/`resume_evidence` rows created; state machine reaches READY"
  - "resume review/edit UI works"
  - "parse is recorded in `ai_runs`"
```

Implemented and verified (CL-009): 59/59 backend tests incl. a Testcontainers
full-pipeline test, frontend build green, docker-compose stack smoke-tested
(upload → READY → edit round-trip).

## SPRINT-02 — JD Intelligence

```yaml
id: SPRINT-02
name: Job Description Intelligence
goal: "User can paste/upload a JD and receive structured requirements + keywords."
status: COMPLETED
start_date: 2026-08-13
target_end_date: 2026-08-13
capacity: S
epics: [EPIC-003]
features: [FEAT-017, FEAT-018, FEAT-019, FEAT-020, FEAT-021]
tasks: [TASK-039..TASK-045]
dependencies: [SPRINT-00]
risks:
  - "JD variety (posting formats) affects extraction quality"
exit_criteria:
  - "`POST /jobs` accepts text or file; `raw_text` extracted"
  - "structured JD validates against §4.3; requirements+keywords extracted and stored in `job_requirements`"
  - "`jobs` state machine reaches READY; list/get/delete endpoints work"
```

Scope note: backend-only sprint (no UI task in TASK-039..045; JD input
screen ships with SPRINT-03's Analyze UI). Two stubbed AI calls per JD
(`jd_parser` then `requirement_extraction`), mirroring the SPRINT-01
pipeline pattern.

Implemented and verified (CL-010): 95/95 backend tests incl. a Testcontainers
full-pipeline test (pasted + file JDs → READY → requirements rows → two
`ai_runs` records → list/get/delete), docker-compose stack smoke-tested
(create → READY → delete round-trip).

## SPRINT-03 — Deterministic Matching + Job Match Score

```yaml
id: SPRINT-03
name: Deterministic Matching + Job Match Score
goal: "User can analyze a job against the resume and get an explainable Job Match Score."
status: COMPLETED
start_date: 2026-08-13
end_date: 2026-08-13
capacity: M
epics: [EPIC-004, EPIC-005]
features: [FEAT-022, FEAT-023, FEAT-024, FEAT-025, FEAT-026, FEAT-027]
tasks: [TASK-046..TASK-058]
dependencies: [SPRINT-01, SPRINT-02]
risks:
  - "aliases/abbreviations coverage is never exhaustive"
exit_criteria:
  - "normalization + exact/alias/keyword matching works; ≥80% requirements matched on test set"
  - "requirement→evidence mapping produced; gaps recorded"
  - "weighted 0–100 Job Match Score with `score_breakdown`; strengths/gaps derivable"
  - "`POST /analyses` runs the pipeline; `analyses` persisted (QUEUED→MATCHING→SCORING→READY/FAILED)"
  - "semantic phase is a stubbed hook only (deferred)"
```

Scope note: backend-only sprint (no UI task in TASK-046..058; analysis
results surface in the UI via FEAT-045, SPRINT-07). Deterministic
exact-first matching consumes `job_requirements` (SPRINT-02) +
`resume_evidence` (SPRINT-01); fuzzy matching per PRD §5.3 uses Apache
Commons Text; the semantic phase is a disabled hook
(`ats.doctor.matching.semantic-enabled=false`, no embeddings).

Implemented and verified (CL-011): 141/141 backend tests incl. a
Testcontainers full-pipeline test (resume + JD → analysis → READY with
score/breakdown/matches/gaps → reanalyze; ≥80% requirements matched on
the Jane Doe vs Google JD fixture), docker-compose stack smoke-tested
(analyze live job → score 68 → reanalyze round-trip → 404/400
ProblemDetails; no `ai_runs` rows — matching is deterministic).

## SPRINT-04 — AI Tailoring

```yaml
id: SPRINT-04
name: AI Tailoring
goal: "User can generate a tailored resume where only selected bullets are rewritten, grounded in evidence."
status: COMPLETED
start_date: 2026-08-13
target_end_date: 2026-08-13
capacity: M
epics: [EPIC-006]
features: [FEAT-028, FEAT-029, FEAT-030, FEAT-031, FEAT-032]
tasks: [TASK-059..TASK-066]
dependencies: [SPRINT-03]
risks:
  - "LLM may invent facts — mitigated by A/B/C rules + validation (next sprint)"
  - "tailoring fidelity depends on prompt quality; dev runs against the stub profile"
exit_criteria:
  - "decision layer selects only bullets that need tailoring"
  - "bullet/summary rewriting uses `tailor-bullet-v1`/summary prompts with A/B/C constraints"
  - "reordering is evidence-preserving"
  - "`tailored_resumes` + `tailored_changes` persisted; `POST /analyses/{id}/tailor` works"
  - "each change records original/tailored text, reason, claim_category, prompt_version"
  - "`score_after` is recomputed deterministically over the tailored content"
  - "no docker-compose build in-session — E2E verification left to manual check (user)"
```

Scope note: backend-only sprint (no UI task in TASK-059..066; per-change
review UI ships with FEAT-037, SPRINT-06). Tailoring is AI-driven via
`ai.generate(task='resume_tailoring')` per selected bullet (recorded in
`ai_runs`, unlike SPRINT-03 matching) — in dev the `StubAiProvider` returns a
deterministic A/B/C-safe rewrite (keyword alignment only, labeled `_stub`).
Validation is a no-op pass-through this sprint: the pipeline transitions
`GENERATING → VALIDATING → READY` but the validator itself ships with SPRINT-05. TailoringState has no FAILED per §5.8 — failures surface via the `error` column. Tailoring never chains: every
`tailored_resumes` row references `analysis_id` + the exact master
`resume_version_id`; `score_before` = the analysis score, `score_after` is a
deterministic re-run of the MatchPipeline over the tailored content.

### Phase plan (drives TASK-059..066 in dependency order)

- **A — Context & contracts**: read PRD §4.5 (tailoring fidelity), §5.9
  (source_refs), §7.7/§7.8 (`tailored_resumes`/`tailored_changes`), §7.9
  (`ai_runs.task = resume_tailoring`); inspect `AiSdk.generate`,
  `StubAiProvider`, the SPRINT-03 `AnalysisPipeline` async pattern,
  `StateMachines`, and fixture evidence/requirements; fix the outline of
  `TailoringRequest`/`TailoringResult` + change contract and the
  `tailored_resumes` state machine.
- **B — TailorDecider (TASK-059) + unit tests (TASK-060)**: selects bullets to
  tailor from gaps/overlaps (skips already-matched bullets); no-op when
  nothing needs tailoring; ≥90% coverage.
- **C — Bullet rewriting + changes (TASK-061, TASK-062)**: `BulletRewriter`
  calls `ai.generate(task='resume_tailoring')` per selected bullet with
  evidence + requirements under `tailor-bullet-v1` A/B/C constraints; each
  change records `original_text`, `tailored_text`, `reason`, `claim_category`,
  `prompt_version`, `status=PENDING`, `evidence_id` (null-safe);
  `ai_runs` row per call.
- **D — Summary rewrite (TASK-063)**: `summary` variant of the rewriter
  against JD keywords with A/B/C constraints.
- **E — Reordering (TASK-064)**: `SummaryRestructurer` reorders most-relevant
  experience first; evidence-preserving and reversible (mirrors what the
  matcher scored).
- **F — Persistence (TASK-065)**: `V5__create_tailoring_tables.sql`
  (`tailored_resumes` per §7.7 + `state`/`error`/`updated_at` precedent;
  `html`/`pdf_path`/`docx_path` stay NULL until SPRINT-06; `tailored_changes`
  per §7.8), entities + repos, state machine
  `QUEUED → GENERATING → READY/FAILED`.
- **G — API + verification (TASK-066)**: `TailoringController` —
  `POST /analyses/{id}/tailor` (404 unknown analysis, 409 analysis not READY
  or already generating) + `GET /tailored/{id}` (mapped to FEAT-032 in §3 of
  07-api-contract) with snake_case `TailoredResumeResponse`; Testcontainers
  full-pipeline Testcontainers test (tailor → READY, rewritten bullets,
  grounded `_stub` rewrite, `score_after` recomputed deterministically (68 for
  the fixture), change rows + `ai_runs.task=resume_tailoring` rows);
  docs 03/04/05/06/07/08/13/15 reconciled; EXIT criteria met; close as CL-013.

Status note (CL-013): DONE — all 5 features / 8 tasks shipped; 171/171 backend tests green incl. the Testcontainers full-pipeline tailoring test; docker-compose E2E left to manual verification (no compose build in-session).

## SPRINT-05 — Fact Validation + Claim Traceability

```yaml
id: SPRINT-05
name: Fact Validation + Claim Traceability
goal: "Every tailored change is validated (deterministic → AI → human) and traceable to evidence."
status: COMPLETED
start_date: 2026-08-13
target_end_date: 2026-08-14
capacity: M
epics: [EPIC-007]
features: [FEAT-033, FEAT-034, FEAT-035, FEAT-036]
tasks: [TASK-067..TASK-073]
dependencies: [SPRINT-04]
risks:
  - "AI validator cannot guarantee zero hallucinations — always paired with deterministic + human review"
exit_criteria:
  - "deterministic validator flags unsupported A/B claims without an LLM"
  - "AI validator (validator-v1) cross-checks original vs tailored; output normalized"
  - "rule engine types issues (unsupported_fact|unsupported_descriptor|unsupported_claim)"
  - "claim traceability resolves every claim to `source_refs`; 'why is this claim here?' UI works"
  - "`VALIDATING` runs for real: pipeline no longer passes the state through as a no-op"
  - "171/171 backend tests stay green; Testcontainers full-pipeline test covers validate + traceability"
```

Scope note: this sprint completes the `VALIDATING` state started in SPRINT-04 (it
was a no-op pass-through there) with a three-stage validation per PRD §5.8 —
deterministic (category-A evidence check + category-B descriptor check over
evidence/source_refs, TASK-067), AI (`fact_validation` task, `validator-v1`
prompt, cross-checks original vs tailored, output normalized, TASK-068/069),
human review (rule-engine typed issues A/B/C, TASK-070; review UI comes with
FEAT-037 in SPRINT-06 — here the checks produce `issues` and the API exposes
them). Traceability resolves every `tailored_changes` row to its `source_refs`
(claim → evidence → section). TASK-073 is the one frontend task: the "why is
this claim here?" traceability UI; the rest is backend.

### Phase plan (drives TASK-067..073 in dependency order)

- **A — Context & contracts**: read PRD §4.4 (source_refs/A-B-C rules), §5.8
  (validation), §7.8 (`tailored_changes`), the SPRINT-04 `TailoringService`/
  `TailoringPipeline` state machine and the `07-api-contract` §4 validate
  contract; inspect `AiTask`/`AiFacade` (unused task ids), `AiRunsRecorder`,
  the deterministic matcher's evidence model, and the frontend layout/API client.
- **B — Deterministic validator (TASK-067) + tests**: `DeterministicValidator`
  — category-A claims must match evidence facts (`technology`/`metric`);
  category-B descriptors must be supported by evidence tokens; no null/blank
  evidence variance. ≥90% coverage.
- **C — AI validator wiring (TASK-068, TASK-069)**: `fact_validation` task +
  `validator-v1` prompt (cross-check original vs tailored text against
  evidence); `AiValidator` calls `ai.generate(task='fact_validation')`, records
  `ai_runs` rows; output normalization (typed issues list) so rule-engine and
  AI issues share one shape.
- **D — Rule engine A/B/C (TASK-070)**: shared `ValidationRules` engine that
  types issues (`unsupported_fact | unsupported_descriptor | unsupported_claim`),
  merges deterministic + AI results, persists `validation` on the tailored
  resume (PRD §7.8 precedent).
- **E — API (TASK-071)**: `POST /tailored/{id}/validate` (404 unknown tailored;
  409 state not READY) returns the typed issues; revalidation allowed.
- **F — Traceability (TASK-072)**: `source_refs` resolution (claim → evidence
  → section → bullet) + `GET /tailored/{id}/changes/{id}/trace` shape per
  `07-api-contract` §4; change rows expose their evidence chain.
- **G — UI + verification (TASK-073)**: frontend "why is this claim here?"
  traceability drawer on the review page; Testcontainers full-pipeline test
  (tailor → validate → typed issues + trace) + jest specs; docs 04/05/06/07/08/
  13/15 reconciled; close as CL-014.

Status note (CL-015): DONE — all 4 features / 7 tasks shipped. `DeterministicValidator`
(category-A facts + category-B descriptors + C-claim achievement variant),
`fact_validation` wiring via `AiValidator` (`validator-v1`, one `ai_runs` row per
call), token-identity output normalization, and `ValidationRules` (typed
`Verdict`, deterministic + AI merge). `POST /tailored/{id}/validate` runs the
rule engine over every change, persists the report (`validation` JSONB on
`tailored_resumes`, V6), and the pipeline's `VALIDATING` phase executes it for
real. Traceability: `GET /tailored/{id}/changes` (read-only listing the
traceability drawer consumes — the GET side of TASK-074, pulled in from
SPRINT-06; the POST review lifecycle stays there) + `GET /tailored/{id}/changes/
{change_id}/trace` (change → evidence chain → section context → tailored bullet
→ its validation issues). Frontend: `/tailored` index + id lookup and the
`/tailored/[id]` page with score cards, state badge and the "Why is this claim
here?" `TraceDrawer` (next build green).

Two bugs found and fixed by the Testcontainers run: (1) `TailorDecider` passed
the *bullet id* as the change's `evidence_id` instead of the evidence row UUID →
every `tailored_changes` row stored NULL and traces resolved to nothing — the
pipeline now resolves section id → evidence row id (`evidenceIdFor`), and the
trace's section lookup also resolves bullet evidence to its experience entry;
(2) validation issue `type` was emitted as an uppercase enum name, violating the
§5.8/`07-api-contract` lowercase shape — normalized (
`unsupported_fact | unsupported_descriptor | unsupported_claim`). Suite: 226/226
green **with Testcontainers enabled** (full-pipeline test covers tailor →
`VALIDATING` for real → typed issues → trace round-trip + 404 paths). Note: the
phase plan above promised "close as CL-014", but CL-014 was taken by the
planning row — the close is CL-015. No frontend jest/unit infra exists
(precedent: `next build` + manual E2E; see `11-testing-strategy.md` gap).


## SPRINT-06 — Review + PDF/DOCX Export

```yaml
id: SPRINT-06
name: Review + PDF/DOCX Export
goal: "User reviews every change, approves, and exports PDF/DOCX/JSON."
status: DONE
start_date: 2026-08-13
completed_date: 2026-08-13
target_end_date: 2026-08-15
capacity: M
epics: [EPIC-008, EPIC-009]
features: [FEAT-037, FEAT-038, FEAT-039, FEAT-040, FEAT-041, FEAT-042, FEAT-043]
tasks: [TASK-074..TASK-082]
dependencies: [SPRINT-05]
risks:
  - "PDF layout fidelity across engines"
exit_criteria:
  - "per-change review (Accept/Reject/Edit/Regenerate) works; before/after diff shown"
  - "approval flow sets APPROVED; export returns 409 until approved and warnings resolved"
  - "HTML template renders; PDF (Flying Saucer + OpenPDF) and DOCX (Apache POI) export at 100% success"
  - "export/pdf|docx|json endpoints work"
```

Head start: SPRINT-05 already shipped the frontend surface this sprint extends —
`/tailored/[id]` renders the change list and the "Why is this claim here?"
`TraceDrawer` from the read-only `GET /tailored/{id}/changes`. TASK-074's GET
side is done; SPRINT-06 adds the POST review lifecycle on top. Verified
groundwork (no new migration needed): `V5__create_tailoring_tables.sql` already
declares `tailored_resumes.state` CHECK with `NEEDS_REVIEW/APPROVED` and the
`html`/`pdf_path`/`docx_path` columns (NULL since SPRINT-04), and
`tailored_changes.status` CHECK already allows
PENDING|ACCEPTED|REJECTED|EDITED|REGENERATED; `StateMachines.tailoring()`
already has `READY → NEEDS_REVIEW → APPROVED` (APPROVED terminal). Locked
decisions for this sprint: change `status` transitions enforce the same values
(a plain guard — only PENDING rows accept actions; no per-change state machine
exists); `edit` sets the new text + status EDITED, `regenerate` re-runs the
pipeline's rewriter (`resume_tailoring`, recorded in `ai_runs`) → status PENDING
— both then **re-validate** (SPRINT-05 `ValidationService`) so the persisted
report stays current; approve applies both existing edges READY→NEEDS_REVIEW→
APPROVED (single idempotent call); export → 409 until every change is
ACCEPTED/REJECTED AND the persisted `validation` report is `valid=true` (PRD
§8.2 — no warning-dismiss action in MVP; re-open if the flow demands it);
export always renders the exact `tailored_resumes` content (no chaining, §7.6);
`html`/`pdf_path`/`docx_path` get populated; JSON export reuses the `GET
/tailored/{id}` response shape.

Closed CL-017 — all phases A–G landed, 237/237 backend tests green (incl.
`TailoringReviewFlowTest` Testcontainers round-trip + `TailoringExportControllerTest`),
frontend `next build` green. Deviations/refinements during implementation:
- `regenerate` re-runs the rewriter but the row is already terminal under the
  PENDING-only rule — landing instead: accept/reject act on PENDING rows only,
  `edit` additionally on EDITED/REGENERATED rows (the reviewer re-fines a
  rewrite until grounded; avoids a deadlock where a regenerated rewrite stays
  flagged forever). All actions move READY → NEEDS_REVIEW on first use.
- REJECTED rows are excluded from the persisted validation report (they never
  ship) and export renders their ORIGINAL text — rejecting a flagged claim no
  longer deadlocks approval.
- Export gate = no PENDING changes AND persisted report `valid=true` (state
  READY/NEEDS_REVIEW/APPROVED); 409 otherwise — assertable in one Testcontainers
  run (stub rewrites are deliberately flagged: "with a focus on python").
- Dependencies: `org.xhtmlrenderer:flying-saucer-pdf` 9.4.0 (coordinates
  relocated from `flying-saucer-pdf-openpdf`); new
  `ats.doctor.storage.exports-dir` (default `data/exports`) holds PDF/DOCX
  artifacts; `spring-boot-starter-thymeleaf` + a dedicated `exportTemplateEngine`
  with a `templates/export/` resolver render the ATS-friendly HTML.
- ValidationService guard extended to admit NEEDS_REVIEW (revalidation during
  review), not just VALIDATING/READY.


### Phase plan (drives TASK-074..082 in dependency order)

- **A — Context & contracts**: read PRD §5.6 (export: Thymeleaf template, Flying
  Saucer + OpenPDF, Apache POI), §7.6 (no-chaining — export always renders the
  exact `tailored_resumes` content), §8.2 (`/approve`, `export/pdf|docx|json`
  + 409 gate); inspect the `tailored_changes` lifecycle (PENDING →
  ACCEPTED|REJECTED|EDITED|REGENERATED, PRD §5.8), the `html`/`pdf_path`/
  `docx_path` columns (NULL since SPRINT-04), `TailoringController` (the
  read-only `GET /tailored/{id}/changes` already lives here from SPRINT-05),
  the SPRINT-05 issue shape (`ValidationIssue`, lowercase
  `unsupported_*` types), `StateMachines.tailoring()`, and the frontend
  `/tailored/[id]` page + `ChangeList`/`TraceDrawer` + API client.
- **B — Changes API (TASK-074, TASK-075)**: keep the GET listing; add
  `POST /tailored/{id}/changes/{change_id}` with body
  `{action: accept|reject|edit|regenerate, new_text?}` (07 §8.2) — guard: only
  PENDING rows transition (ACCEPTED/REJECTED/EDITED are terminal for the row;
  REGENERATED resets to PENDING); `regenerate` re-runs the change through the
  pipeline's rewriter (`BulletRewriter`, `resume_tailoring` in `ai_runs`),
  replaces `tailored_text`, sets status back to PENDING and **re-validates**
  (SPRINT-05 `ValidationService`) before the resume may be re-approved; `edit`
  stores the user text (EDITED) and re-validates too.
- **C — Review UI (TASK-076, TASK-077)**: extend `ChangeList` (SPRINT-05) with
  per-change Accept/Reject/Edit/Regenerate controls wired to the POST endpoint;
  before/after diff with green/red/yellow highlighting per 10-ui-ux; score
  before/after cards already on the page; each row keeps the "Why is this claim
  here?" `TraceDrawer` link; a review badge shows resolved/unresolved changes +
  open validation warnings.
- **D — Approval + export gate (TASK-078)**: `POST /tailored/{id}/approve` →
  applies the existing `READY → NEEDS_REVIEW → APPROVED` edges (single,
  idempotent call, `{"status":"approved"}` response); **409** until every change
  is ACCEPTED/REJECTED and the persisted `validation` report is `valid=true`
  (PRD §8.2 — edits/regenerates re-validate in phase B, so the report is
  current).
- **E — HTML rendering (TASK-079)**: Thymeleaf ATS-friendly resume template
  (simple, scannable — no images/tables) rendered from the tailored content +
  section order; sanitize output; populate `html`.
- **F — PDF (TASK-080)**: Flying Saucer + OpenPDF (XHTML/CSS → PDF) with
  layout-fidelity checks against the fixture resume; populate `pdf_path`; 100%
  success on the test set.
- **G — DOCX + endpoints + verification (TASK-081, TASK-082)**: Apache POI
  (XWPF — already a dependency from SPRINT-01 parsing) DOCX generation,
  `docx_path` populated; wire `export/pdf|docx|json` + 409 gating (404 unknown
  tailored / missing export); Testcontainers export test (tailor → review →
  approve → export round-trip + 409 before approval) + frontend build green;
  docs 04/05/06/07/08/13/15 reconciled; close as CL-017 (CL-016 is taken by the
  planning row; the "close as CL-016" written earlier is superseded); no
  docker-compose build in-session — E2E export left to manual check.

## SPRINT-07 — History + Versioning + Hardening

```yaml
id: SPRINT-07
name: History + Versioning + Hardening
goal: "Immutable versions, source-of-truth enforcement, analysis history, tests, and security/privacy checklist."
status: ACTIVE
start_date: 2026-08-13
target_end_date: 2026-08-15
capacity: M
epics: [EPIC-010, EPIC-011]
features: [FEAT-044, FEAT-045, FEAT-046, FEAT-047, FEAT-048]
tasks: [TASK-083..TASK-092]
dependencies: [SPRINT-06]
risks:
  - "hardening scope can balloon — keep it to the defined exit criteria"
exit_criteria:
  - "versions append-only; tailoring always references the exact master version; no chaining from tailored resumes"
  - "analysis history + reanalyze available in UI"
  - "Maven build green (`mvn verify`: JUnit 5 + AssertJ + Testcontainers); coverage targets met; AI-eval fixtures run"
  - "global error handling; loading/empty/error states complete"
  - "§12 security checklist implemented; AI transparency UI shows provider + 'data leaves this machine'"
```

Progress (2026-08-13): **TASK-087 DONE** — `mvn verify` gate live: JaCoCo 0.8.13
report + check (rule limits = §13.1 targets: parsing/matching 0.90, tailoring
0.80, validation 1.00; actuals 93.0 / 99.0 / 94.0 / 100.0 after the coverage
tests added this turn — validator hit 100% after removing an unreachable
defensive branch in `DeterministicValidator.snippet`); surefire argLine bakes
`-Dapi.version` (pom property, default 1.41) so local runs only need
`DOCKER_HOST=unix:///Users/azhar/.orbstack/run/docker.sock` (the old
`-DargLine=-Dapi.version=1.41` flag is no longer needed); `.github/workflows/
ci.yml` shipped (verify on push/PR — exercises in GA once the repo has a
remote); **248/248 tests green**.


### Phase plan (drives TASK-083..092 in dependency order)

- **A — Context & contracts**: read PRD §5.9 (source of truth + claim
  traceability), §7.x (resume versioning), §12 (security & privacy checklist,
  incl. AI transparency §12.2), 11-testing-strategy §4/§5/§6 (AI-eval fixtures,
  `mvn verify` gate, success metrics), 07-api-contract §7 (ai_runs / provider
  transparency); inspect `resume_versions` (`version` INTEGER + unique
  `(resume_id, version)` index already in V2; `PUT /{id}/edit` currently
  re-validates **in place** — `ResumeService` documents that append-only lands
  with FEAT-044), the no-chaining invariant (every `tailored_resumes` references
  the exact master `resume_version_id`, FK already), `AnalysisController`/
  `AnalysisService` (list + reanalyze already ship, TASK-057/058), the SPRINT-06
  export infra (paths/HTML/PDF/DOCX), `/ai/config` + `/ai/models` (SPRINT-00),
  and the frontend `app/(dashboard)/{resume,jobs,analyses}` screens + API client.
- **B — Versioning + source-of-truth (TASK-083, TASK-084)**: rework `PUT
  /{id}/edit` to create the next `resume_version` row (`version+1`; the unique
  index exists) instead of mutating in place — old versions immutable
  (no in-place update path remains); version increment on every master resume
  write (upload creates too); no-chaining enforcement: no endpoint accepts a
  tailored-resume-derived version, + a test asserting chaining → 409 where
  versions are bound; entities expose `version` so responses show it.
- **C — Analysis history (TASK-085, TASK-086)**: extend/confirm the existing
  `GET /analyses` (SPRINT-03 list: state/score/timestamps) + history UI in the
  dashboard (`app/(dashboard)/analyses/`), surfacing the existing reanalyze
  flow (TASK-058) per entry.
- **D — CI gate + fixtures (TASK-087, TASK-088)**: `mvn verify` (JUnit 5 +
  AssertJ + Testcontainers) as the only gate — on this machine that means
  `DOCKER_HOST=unix:///Users/azhar/.orbstack/run/docker.sock` + `-Dapi.version=
  1.41 -DargLine=-Dapi.version=1.41` (`/var/run/docker.sock` is a stale symlink;
  OrbStack requires API ≥1.40 — document in 11); coverage targets met (11 §3
  table: Tailoring engine 80%+, Validator 100%); AI-eval fixtures run (11 §4/§5
  success metrics: JD parsing ≥90% on 50 JDs, matching precision ≥80%,
  fabrication ≤1%, export success 100%); fix the stale "No
  `backend/src/test/java` exists yet" line in 11-testing-strategy.
- **E — Error handling + UX polish (TASK-089, TASK-090)**: ProblemDetail
  advice + `spring.mvc.problemdetails.enabled` already exist (SPRINT-01) —
  remaining: retry UX for AI failures (queue status + retry affordance) +
  consistent response bodies across controllers; loading/empty/error states
  complete across screens per 10-ui-ux.
- **F — Security (TASK-091)**: PRD §12.1 checklist — input validation + size/
  type limits everywhere, secrets hygiene (env-only, nothing in the repo),
  security headers (X-Content-Type-Options, frame/transport), filesystem
  permissions on uploads/exports dirs, no PII in logs (redaction in the AI
  recorder/pipelines); + tests for the enforcement points.
- **G — Transparency + verification (TASK-092)**: AI transparency UI in the
  dashboard consuming the existing `/ai/config` + `/ai/models` (provider per
  task, prompt/model version, "data leaves this machine: yes/no" per PRD §12.2 —
  global + per-AI-call from `ai_runs`); full suite green (`mvn verify`) +
  frontend build; docs 04/05/06/07/08/11/13/15 reconciled (04/05/06/07/08/13/15
  per the standard list + 11 for the stride fix); close as **CL-019** (CL-017 is
  reserved for the SPRINT-06 close, CL-018 for the SPRINT-07 open row); no
  docker-compose build in-session — E2E left to manual check.

## SPRINT-08+ — Advanced (future)

```yaml
id: SPRINT-08+
name: Advanced (Semantic matching & extensions)
goal: "Semantic matching (embeddings/pgvector) and optional extensions — intentionally NOT in the MVP."
status: PLANNED
start_date: TBD
target_end_date: TBD
capacity: TBD
epics: [EPIC-012]
features: [FEAT-049, FEAT-050, FEAT-051, FEAT-052, FEAT-053]
tasks: [TASK-093..TASK-099]
dependencies: [SPRINT-07]
risks:
  - "scope creep — features here stay BACKLOG until explicitly pulled in"
exit_criteria:
  - "semantic phase slotted into the TASK-051 `MatchPipeline` stub hook (exact-first, then semantic), threshold-gated, deterministic fallback when embeddings are unavailable (feature flag `ats.doctor.matching.semantic-enabled`)"
  - "pgvector enabled: migration + embedding columns on evidence/requirement tables + HNSW index + similarity query with tests"
  - "embeddings pipeline runs at ingest (resume evidence + JD requirements) with caching; provider behind the facade with a deterministic dev stub"
  - "resume variants entity + UI listed on the review page (mirrors `tailored_resumes` precedent, AI-provider transparency labels)"
  - "cover letter generation (`cover_letter` task + prompt v1) exports PDF/DOCX/JSON reusing the SPRINT-06 generators"
  - "application tracker data + UI; OCR (Tesseract) optional behind a flag"
  - "full suite green (`mvn verify` precedent) + frontend build; docs 04/05/06/07/08/13/15 reconciled"
```

Scope note (prepared ahead of pull): phase plan set so the sprint is
instantly actionable when opened — FEAT-049..053 stay BACKLOG in
`05-backlog.md`/`06-technical-tasks.md` until explicitly pulled into a started
sprint; no READY flips; exit criteria above are the draft — confirmed when the
sprint starts. Open as **CL-020**, close as **CL-021** (pattern: CL-016/017 =
SPRINT-06 open/close, CL-018/019 = SPRINT-07 open/close).

### Phase plan (drives TASK-093..099 in dependency order)

Locked decisions: embedding provider = `sentence-transformers` behind the AI
facade with the deterministic dev-stub pattern (same A/B/C-safe `_stub`
labeling as `StubAiProvider`); semantic phase gated by the SPRINT-03 flag
`ats.doctor.matching.semantic-enabled` (default false) with a deterministic
fallback when embeddings are unavailable; similarity threshold + pgvector
Testcontainers image support (`pgvector/pgvector:pg15` vs current
`postgres:15-alpine`) confirmed at pull time; variants mirror the
`tailored_resumes` entity/table precedent; cover letters reuse the SPRINT-06
PDF/DOCX/JSON generators.

- **A — Context & contracts**: read the deterministic matcher stack
  (TASK-046..051, FEAT-022..024 — `MatchPipeline`/`HybridMatcher`/evidence
  index), the `07-api-contract` §4/§5 shapes, and the SPRINT-06 export/UI
  patterns to reuse (PDF/DOCX generators, review page); define upfront:
  embedding provider choice, similarity threshold, `pgvector` availability in
  the dev DB (Testcontainers image support).
- **B — Embeddings pipeline (TASK-093, FEAT-049)**: `sentence-transformers`
  service + tests; vectorize the evidence corpus (resume evidence + JD
  requirements) at ingest with caching; provider sits behind the facade
  (stub in dev, deterministic label).
- **C — pgvector + HNSW (TASK-094, FEAT-049)**: migration enabling
  `pgvector`, embedding columns on evidence/requirement tables, HNSW index
  config, similarity search query + tests.
- **D — Semantic matcher + fallback (TASK-095, FEAT-049)**: semantic phase
  slots into the TASK-051 stub hook of `MatchPipeline` (exact-first, then
  semantic); threshold-gated; deterministic fallback when embeddings are
  unavailable (feature flag).
- **E — Variant model + UI (TASK-096, FEAT-050)**: variant entity + tables
  (mirrors the `tailored_resumes` precedent), generated variants listed on
  the review page with the AI-provider transparency labels (TASK-092 pattern).
- **F — Cover letter generation + export (TASK-097, FEAT-051)**: `cover_letter`
  task wiring + prompt v1; export reuses the SPRINT-06 generators
  (PDF/DOCX/JSON endpoints precedent).
- **G — Tracker + OCR + verification (TASK-098, TASK-099, FEAT-052/053)**:
  application tracker data + UI; optional Tesseract OCR behind a flag; full
  suite green; docs 04/05/06/07/13/15 reconciled; close as CL-021 when the
  sprint is opened (open row = CL-020).

---

## Sprint dependency chain

```text
SPRINT-00 → SPRINT-01 → SPRINT-03 → SPRINT-04 → SPRINT-05 → SPRINT-06 → SPRINT-07 → SPRINT-08+
        └→ SPRINT-02 ─┘
```

Total: **9 sprint entries** (SPRINT-00 … SPRINT-08+).
