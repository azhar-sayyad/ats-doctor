# 12 — Release Plan

Per PRD v1.2 §10 (Implementation Roadmap). Three deliberate MVP steps — v0.1
is small on purpose. A sprint is only "done" when its output works end-to-end
on real resume/JD pairs (AI evaluation, PDF rendering, and prompt tuning take
longer than naive estimates).

---

## v0.1 — Core Pipeline (SPRINT-00..05)

> Focus: get the exact-match workflow working end-to-end. **No embeddings, no
> DOCX, no version editor, no model-config UI.**

1. Build the **AI Abstraction Layer** (AIService + Task Router + profiles) over OmniRoute.
2. Upload master resume → extract text → parse → store structured resume (with `ai_runs` + state machines).
3. Paste/upload JD → parse → extract requirements & keywords.
4. Exact + fuzzy matching (no semantic search yet).
5. Job Match Score (weighted, explainable).
6. Tailor **selected** bullets (decision layer + LLM).
7. Deterministic + LLM fact-grounding validation.
8. Export PDF.

Sprints: SPRINT-00 Foundation (ACTIVE) → SPRINT-01 Master Resume →
SPRINT-02 JD Intelligence → SPRINT-03 Matching + Scoring → SPRINT-04 AI
Tailoring → SPRINT-05 Fact Validation + Claim Traceability.

Exit = **MVP v0.1**: upload/parse resume → parse JD → exact match → Job Match
Score → tailor selected bullets → validate → export PDF (PRD §16).

## v0.2 — Review & Refinement (SPRINT-06..07)

- Semantic matching (embeddings) — only for ambiguous cases.
- Resume evidence model (claim categories A/B/C, `source_refs`).
- Per-change review UI (Accept/Reject/Edit/Regenerate).
- Claim-traceability UI ("why is this claim here?").
- Resume versioning + source-of-truth enforcement.
- DOCX export.
- Analysis history.

Sprints: SPRINT-06 Review + Export → SPRINT-07 History + Versioning +
Hardening.

## v0.3 — Advanced (SPRINT-08+, BACKLOG)

- Claim traceability (full).
- Advanced scoring / explanation.
- Multiple resume variants.
- Cover letters.
- Application tracking.
- Semantic matching / embeddings / pgvector (FEAT-049, EPIC-012).

## Feature/status derivation

| Release | Sprints | Epics | Features |
|---------|---------|-------|----------|
| v0.1 (MVP) | SPRINT-00..05 | EPIC-001..007 | FEAT-001..036 |
| v0.2 | SPRINT-06..07 | EPIC-008..011 | FEAT-037..048 |
| v0.3+ | SPRINT-08+ | EPIC-012 | FEAT-049..053 |

## Release gates

- v0.1: all P0 features in EPIC-001..007 DONE; export PDF works end-to-end;
  fabrication rate ≤1% on test fixtures; Job Match score improvement +10–20%.
- v0.2: per-change review + traceability + DOCX export verified; tests pass.
- v0.3: semantic matching shipped; no regressions in exact-match path.

## Open items affecting plan (PRD §15.2)

- OCR for scanned PDFs → deferred (FEAT-053, v0.3+, Tesseract optional).
- Very large resumes → warn user if >50 bullets.
- Multiple master resumes → post-MVP (FEAT-050).
- AI API failures → fallback to local model or controlled error (already in facade design).
