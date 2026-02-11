# 11 — Testing Strategy

Per PRD v1.2 §13. Structure: unit → integration → end-to-end → AI evaluation.
**Nothing is DONE until acceptance criteria in `06-technical-tasks.md` are met
and tests pass.** Current status (2026-08-13, TASK-087): 248/248 backend tests
green via the `mvn verify` CI gate (JaCoCo check enforces the §13.1 limits
below); AI-eval fixture suite is TASK-088. The stale "No
`backend/src/test/java` exists yet" note from the initial scaffold has been
retired — the suite is at `backend/src/test/java/com/atsdoctor/backend/`.

---

## 1. Unit tests (PRD §13.1)

| Component | Coverage | Tools | Tasks |
|-----------|----------|-------|-------|
| Resume parser | 90%+ | JUnit 5 + AssertJ | TASK-029/030, TASK-031 |
| JD parser | 90%+ | JUnit 5 + AssertJ | TASK-040/041 |
| Matching engine | 90%+ | JUnit 5 + AssertJ | TASK-046/047/049 |
| Tailoring engine | 80%+ | JUnit 5 + AssertJ | TASK-059/060 |
| Validator | 100% | JUnit 5 + AssertJ | TASK-067/070 |
| AI router/profiles/fallback/prompts | — | JUnit 5 + Mockito | TASK-017 |
| State machines | — | JUnit 5 | TASK-021 |
| Normalization utilities | — | JUnit 5 | TASK-046 |
| Scoring engine | — | JUnit 5 | TASK-052 |

Actual coverage (JaCoCo, TASK-087, line %): Resume+JD parser 93.0% (target
90%), Matching engine 99.0% (target 90%), Tailoring engine 94.0% (target 80%),
Validator 100% (target 100%) — enforced by the `jacoco-maven-plugin` `check`
goal (`verify` phase, rule limits = the targets above).

Deterministic components (exact/alias/keyword matching, scoring, state
machines, validators) must have **no** AI dependency in tests — use real
fixtures, not mocks, where possible.

## 2. Integration tests (PRD §13.2)

| Scenario | Test case | Tasks |
|----------|-----------|-------|
| Upload → Parse → Store | PDF/DOCX resume end-to-end | TASK-026..028, TASK-035/036 |
| Paste JD → Parse → Store | Text JD end-to-end | TASK-039, TASK-044/045 |
| Matching pipeline | JD + resume → score + evidence | TASK-056/057 |
| Tailoring pipeline | JD + resume → tailored resume | TASK-065/066 |
| Export pipeline | Tailored resume → PDF/DOCX | TASK-079..082 |

Integration tests run against a real PostgreSQL via **Testcontainers**, with
stub AI where the provider is not the subject under test.

## 3. End-to-end tests (PRD §13.3)

| Flow | Steps |
|------|-------|
| Full user journey | Upload resume → Paste JD → Analyze → Tailor → Validate → Export |
| Fact-grounding detection | Tailor resume with unsupported Category C claim → validator flags it |
| Multi-JD analysis | Analyze 3 JDs sequentially → verify no data leakage |

## 4. AI evaluation (fixture-based)

AI tasks are tested for **structural correctness**, not exact text:

| Fixture | Purpose |
|---------|---------|
| 10+ sample resumes | Varied formats/complexity (PRD §13.4) |
| 20+ sample JDs | Varied roles/industries (PRD §13.4) |
| Edge cases | Empty resume/JD; tables/images (fail gracefully); JD with no skills |
| Adversarial tailoring cases | Category C attempts must be rejected/flagged |
| Versioned prompt fixtures | One expected-shape output per prompt version |

Acceptance for AI tasks: output matches the Java record schema (§4.2/§4.3),
fields within allowed enums, and no invented A/C claims (validator).

## 5. CI / runner (FEAT-046, TASK-087)

- `mvn verify` (JUnit 5 + AssertJ + Testcontainers) is the only gate — runs the
  full suite **and** the JaCoCo coverage check (limits per §13.1, §3 above).
- Local: `DOCKER_HOST=unix:///Users/azhar/.orbstack/run/docker.sock ./mvnw verify`
  (OrbStack; the surefire argLine bakes in `-Dapi.version` so no extra flags).
  CI ships as `.github/workflows/ci.yml` (Temurin 21 + `docker/setup-docker-action`).
- Backend boot + AI layer smoke test (TASK-025): `GET /api/v1/health` returns OK
  and reports AI mode (`omniroute | stub`).
- OmniRoute config validation test (TASK-019).

## 6. Success metrics gate (PRD §11.1)

Tests back these targets: resume parsing accuracy ≥95% (50 resumes manual
review), JD parsing ≥90% (50 JDs), matching precision ≥80%, fabrication rate
≤1%, Job Match score improvement +10–20%, export success 100%.

## 7. Current gaps

The backend test suite (248/248 green incl. Testcontainers, enforced by the
`mvn verify` JaCoCo gate — see the OrbStack `DOCKER_HOST` note in
`13-project-status.md`) is current with SPRINT-07's TASK-087. Shared
integration test fixtures (resumes/JDs) are planned in TASK-088
(`integration test fixtures`); AI-eval fixture runs land with the SPRINT-07
CI gate. Frontend remains `next build` + manual E2E — no jest/unit infra yet
(open gap, TASK-090 carries the loading/empty/error-state checks).
