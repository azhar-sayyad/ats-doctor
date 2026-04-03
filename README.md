# ATS Doctor

Local-first, AI-provider-agnostic, single-user resume optimization system.

**Source of truth:** `docs/ATS-Doctor-PRD.md` (v1.2) — see `docs/README.md` for
the documentation set.

## Stack (v1.2, see `docs/14-decision-log.md` DEC-023..033)

- **Backend:** Java 21, Spring Boot modular monolith (Spring Web, Spring Data
  JPA + Hibernate, Spring AI, Flyway), Apache PDFBox / Apache POI parsing,
  Thymeleaf + Flying Saucer + OpenPDF export.
- **Frontend:** Next.js 14 (App Router) — UI only, no API routes.
- **Database:** PostgreSQL 15 (Docker), versioned migrations via Flyway.
- **AI:** `AIService → Task Router → Model Profiles → Spring AI → OmniRoute`
  (provider-agnostic; DeepSeek is a deployment-only default).

## Quick start

```bash
cp .env.example .env        # default: AI mode = stub (no API keys needed)
docker compose up --build
```

Then:

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8000/api/v1 |
| Health | http://localhost:8000/api/v1/health |
| PostgreSQL | localhost:5432 (ats/ats/ats) |

Backend alone (no Docker, stub AI):

```bash
cd backend && mvn spring-boot:run
```

Frontend alone:

```bash
cd frontend && npm install && npm run dev
```

## AI mode

- `ATS_DOCTOR_AI_MODE=stub` (default): deterministic stub provider — no API
  keys, fully offline. Good for development and CI.
- `ATS_DOCTOR_AI_MODE=omniroute`: delegates chat completions to OmniRoute via
  `SPRING_AI_OPENAI_BASE_URL` (see `.env.example`).

OmniRoute itself is deployment-only and external. For local end-to-end work in
`omniroute` mode without a real gateway, run the included dev stand-in
(`omniroute/dev-gateway/mock_gateway.py`) and point the base URL at it:

```bash
python3 omniroute/dev-gateway/mock_gateway.py 8080 &
ATS_DOCTOR_AI_MODE=omniroute \
SPRING_AI_OPENAI_BASE_URL=http://localhost:8080 \
cd backend && mvn spring-boot:run
```

## Repository layout

```text
backend/    Spring Boot app (Java 21, Maven) — see PRD §14
frontend/   Next.js 14 app (UI only)
omniroute/  AI gateway config (deployment-only)
data/       Local storage (backend-only Docker mount)
docs/       PRD v1.2 + living documentation set
```

## Status

SPRINT-00..06 (Foundation → Review + Export) are **DONE** — 248/248 backend
tests green via the `mvn verify` gate, frontend `next build` green. SPRINT-07
(History + Versioning + Hardening) is **ACTIVE**. See `docs/13-project-status.md`.
