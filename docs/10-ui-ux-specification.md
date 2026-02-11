# 10 — UI/UX Specification

Screens per PRD v1.2 §5.7, driven by processing state machines (PRD §5.8). The
frontend is a Next.js app (app router, UI only — no API routes). Every screen
must handle loading / empty / error / success states; every async action shows
the backend entity's processing status.

**Status note**: frontend is implemented and verified through SPRINT-06 —
`app/(dashboard)/{resume,jobs,analyses}`, `app/tailored` (index, detail,
ReviewPanel, TraceDrawer), marketing home, Navbar with AI-provider
transparency badge. Screens below marked "pending" are SPRINT-07 (FEAT-044/
045/047/048) work. Frontend has no jest/unit infra — `next build` + manual
E2E is the current gate (see 11-testing-strategy §7 gap).

---

## 1. Screen inventory

| # | Screen | Purpose | Feature(s) | Key API calls (07-api-contract) |
|---|--------|---------|-----------|----------------------------------|
| 1 | Dashboard | Master resume status + JD input + recent analyses | FEAT-003, FEAT-017, FEAT-045 | `GET /resumes/current`, `GET /analyses`, `POST /jobs` |
| 2 | Resume Review | Review/edit structured master resume | FEAT-016 | `GET /resumes/{version_id}`, `PUT /resumes/{version_id}/edit` |
| 3 | Job Analysis | Score, strengths, gaps, requirement breakdown | FEAT-027, FEAT-026 | `POST /analyses`, `GET /analyses/{analysis_id}` |
| 4 | Tailored Review | Before/after + validation warnings + changes summary | FEAT-038, FEAT-039 | `GET /tailored/{tailored_id}`, `POST /tailored/{tailored_id}/validate` |
| 5 | Per-Change Review | Accept/Reject/Edit/Regenerate per bullet | FEAT-037 | `GET /tailored/{id}/changes`, `POST /tailored/{id}/changes/{change_id}` |
| 6 | Claim Traceability | "Why is this claim here?" | FEAT-036 | `GET /tailored/{tailored_id}` (source_refs) |
| 7 | Export | PDF / DOCX / JSON download with gate | FEAT-043 | `POST /tailored/{id}/approve`, `GET /tailored/{id}/export/*` |
| 8 | History | Past analyses + versions | FEAT-045 | `GET /resumes/versions`, `GET /analyses` |
| 9 | AI Transparency | Provider + data-leaves-machine display | FEAT-048 | `GET /ai/config` |
| 10 | AI Config (optional) | Edit model assignments | FEAT-009 | `GET/PUT /ai/config`, `GET /ai/models` |
| 11 | Resume Upload/Processing | Upload + progress through EXTRACTING/PARSING | FEAT-010, FEAT-013 | `POST /resumes/upload` |
| 12 | JD Input | Paste text or upload file | FEAT-017 | `POST /jobs` |

## 2. Screen details

### Screen 1: Dashboard
- Master resume card: parsed status (`✅ Parsed: name — title`, counts of
  experiences/skills/projects, last updated) with `[View] [Replace] [Edit]`.
  Shows Resume state machine states while processing (PRD §5.8).
- "Analyze a Job Description": `[Paste JD] [Upload PDF/DOCX]` + `[Analyze Job]`.
- Recent analyses list: `🎯 Title @ Company — N% Match` + date.
- Loading: skeletons; Empty: "No resume yet — upload one"; Error: inline error.

### Screen 2: Job Analysis
- `JOB MATCH SCORE: N/100` progress bar. **Note under score** (PRD §5.7): the
  score is a **Job Match Score** — an estimate of alignment, **not** an actual
  ATS score.
- Strengths table (Skill | Evidence with section ids) and Gaps table
  (Requirement | Suggestion).
- Requirements breakdown: each requirement with `✅ MATCHED` (evidence + %
  similarity) or `❌ NO MATCH`.
- `[Tailor Resume]` action. Poll `analysis.status` while QUEUED/MATCHING/SCORING.

### Screen 3: Tailored Resume Review
- Header: `JOB MATCH: 84% → 91% (+7%)`.
- Before/After columns; validation warnings block (`⚠`); changes summary
  (added/reordered/review-pending).
- Actions: `[Edit] [Regenerate] [Approve & Export PDF] [Export DOCX]`.

### Screen 4: Per-Change Review (central to UX)
- Original / Proposed / Why? / Evidence (✓ evidence ids).
- `[Accept] [Reject] [Edit] [Regenerate]` per bullet.
- Export is **blocked** until every change is accepted/rejected and warnings
  are resolved (409 gate).

### Screen 5: Claim Traceability
- Click "Why is this claim here?" on any generated claim → show supporting
  evidence (`source_refs`). Core MVP feature (PRD §5.9).

### Screen 6: AI Transparency
- Per-task: `AI Provider: DeepSeek V4 Flash` / `Data leaves this machine: Yes`
  or `AI Provider: Local Model` / `Data leaves this machine: No` (PRD §12.2).

## 3. State handling

| State | Behavior |
|-------|----------|
| Loading | Skeletons/spinners; disabled actions while a pipeline is running |
| Empty | Guidance copy ("No resume yet", "No analyses yet") + primary action |
| Error | Inline error + retry; backend `FAILED` states surfaced (e.g., parse failed) |
| Success | Navigate to result screen or show inline confirmation |

## 4. Frontend stack (PRD §9, §14)

- Next.js 14 app router — UI only, **no API routes**.
- Tailwind + shadcn/ui base (TASK-010).
- `frontend/app/(dashboard)/{page,resume,jobs,analyses}/`, `frontend/components/`,
  `frontend/lib/`, `frontend/styles/`.
- Dashboard shell + backend health check (TASK-011, `GET /api/v1/health`).
