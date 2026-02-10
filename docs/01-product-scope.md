# 01 — Product Scope

Derived from PRD v1.2 §1–2, §11, §15.1. This document defines what ATS Doctor
is and — equally important — what it is not.

## Product summary

ATS Doctor is a **local-first, AI-provider-agnostic, single-user resume
optimization system**. The user pastes/upload a job description, clicks
"Analyze," and receives a tailored, ATS-optimized resume — locally, privately,
without fabrications, and without being locked to any AI vendor.

- **Local-first** (PRD v1.2 changelog #1): local storage default; AI inference
  may use a configured external provider with explicit user consent.
- **Fact-grounded tailoring**: the tool rewrites and emphasizes real evidence;
  it never invents facts.
- **Master resume = source of truth**: immutable; every tailored resume
  references a specific version.

## Target audience

| Audience | Description | Scale |
|----------|-------------|-------|
| Job seekers | Individual users tailoring resumes | Single-user, no auth |

## Deployment model

- Local deployment via Docker Compose (frontend, backend, PostgreSQL, optional OmniRoute/Ollama).
- Localhost-first; no cloud storage; no telemetry.
- Backend is the only service with filesystem access (`./data`).
- External AI providers are supported through the AI Abstraction Layer + OmniRoute,
  but no vendor is required for the core experience (dev runs in stub mode).

## Goals

### Primary goals (MVP)
1. Upload a master resume (PDF/DOCX) and extract structured data + evidence.
2. Parse a JD and extract requirements + keywords.
3. Compute an explainable **Job Match Score** (exact-first matching).
4. Tailor **selected** bullets to close gaps (decision layer + LLM).
5. Validate every change against the evidence model (defense-in-depth).
6. Export the approved result as PDF/DOCX.

### Secondary goals (post-MVP)
- Semantic matching for ambiguous cases, multiple resume variants, cover
  letters, application tracking, analysis history polish, OCR for scanned PDFs.

## Non-goals (explicitly excluded)

1. Multi-user accounts, auth, roles, or data isolation.
2. Cloud hosting / SaaS overhead (payments, subscriptions, public hosting).
3. Guaranteeing actual ATS acceptance (the score is an alignment estimate, not an ATS score).
4. "Hallucination-proof" output — no LLM validator can guarantee zero hallucinations.
5. A graph database — relational tables + JSONB instead.
6. Vendor lock-in — application code must never call a vendor SDK directly.
7. Scanned-PDF OCR in MVP (Tesseract optional, deferred).
8. Embeddings / pgvector in MVP (exact matching suffices).

## Success metrics (PRD §11)

### Functional
| Metric | Target | Measurement |
|--------|--------|-------------|
| Resume parsing accuracy | ≥95% | Manual review of 50 test resumes |
| JD parsing accuracy | ≥90% | Manual review of 50 test JDs |
| Matching precision | ≥80% | % of JD requirements correctly matched |
| Fabrication rate | ≤1% | % of tailored resumes with unsupported Category A/C claims |
| Job Match score improvement | +10–20% | Average score increase after tailoring |
| Export success rate | 100% | % of exports without errors |

### Performance
| Metric | Target |
|--------|--------|
| Resume parsing time | <10s |
| JD parsing time | <10s |
| Matching time | <5s (100 resume bullets) |
| Tailoring time | <30s (depends on LLM) |
| UI initial load | <2s |

### User (future, single-user analytics)
- 1 daily active user; 3–5 sessions/day; 2–3 JDs analyzed/session.

## Key assumptions (PRD §15.1)

1. Single-user; no multi-user auth or data isolation.
2. Local deployment; Docker Compose is sufficient.
3. OmniRoute handles model switching; local models (Ollama) optional.
4. Master resume ≤100 bullets (evidence sets stay manageable).
5. JDs ≤5 pages (text extraction reliable).
6. Master resume is immutable; tailoring references a specific version.

## Terminology (must be used consistently)

| Use | Never use | Reason |
|-----|-----------|--------|
| Job Match Score | ATS Score | We cannot know proprietary ATS algorithms |
| Fact-grounded | Hallucination-proof | No LLM can guarantee zero hallucinations |
| Local-first | 100% Local | External AI is allowed with consent |
| Claim categories A/B/C | — | Drives tailoring + validation |
| Profile (`cheap`/`quality`/`local`/`private`) | vendor/model names in app code | Provider-agnostic architecture |

## References

- Full requirements: PRD v1.2 (§1–16)
- Feature breakdown: [`02-epics.md`](./02-epics.md), [`03-features.md`](./03-features.md)
- Release boundaries: [`12-release-plan.md`](./12-release-plan.md)
