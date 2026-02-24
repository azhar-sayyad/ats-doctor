# 16 — Stub AI Provider Analysis

Architecture, working, and accuracy of the deterministic dev/CI provider
(`StubAiProvider`). Active by default via
`@ConditionalOnProperty(name = "ats.doctor.ai.mode", havingValue = "stub",
matchIfMissing = true)` so the app boots fully offline with no API keys
(PRD §6, TASK-025 smoke test, DEC-024).

Source: `backend/src/main/java/com/atsdoctor/backend/infrastructure/ai/StubAiProvider.java`.

---

## 1. Architecture

### Provider seam

`AiProvider` (`infrastructure/ai/AiProvider.java:9`) is the provider contract:
`name()` + `complete(ProviderRequest)`. Two implementations exist:

| Provider | Active when | Notes |
|----------|-------------|-------|
| `StubAiProvider` | `ats.doctor.ai.mode=stub` (default) | Deterministic, offline, CI-safe |
| `SpringAiChatGateway` | `ats.doctor.ai.mode=omniroute` | Real model via Spring AI → OmniRoute |

App code never touches providers directly — everything goes through the AI
abstraction layer (`AIService` → `AiFacade` → `TaskRouter` → `ModelProfileResolver`
→ `AiProvider`). See `docs/09-ai-specification.md`.

### Call path (`application/ai/AiFacade.java`)

1. Resolve task → model profile (`AiFacade.java:48`).
2. Select `activeProvider()` — the first bean in the injected
   `List<AiProvider>`; exactly one is active per mode (`AiFacade.java:138`).
3. **Prompt-render + variables split** — `AiFacade` replaces
   `request.input()` with the fully rendered prompt but keeps the original
   per-slot values in `request.variables()` (`AiFacade.java:55-56`). This is
   the key trick: the stub reads raw user text back from the variables map
   instead of echoing the prompt (`StubAiProvider.java:31-34`).
4. Retries with exponential backoff bounded by `timeout-seconds`
   (`AiFacade.java:62-79`), single configured fallback per profile
   (`AiFacade.java:81-85`), then dev/test degradation to a labeled stub result
   via `degradeToStub()` (`AiFacade.java:87-89`, `108-118`).
5. Every execution is recorded in `ai_runs` with a SHA-256 of the prompt
   input (`AiFacade.java:120-136`).

### Task → implementation map

| Task | Path | Source |
|------|------|--------|
| `resume_parser` | `RawTextResumeParser.parse(...)` (heuristic) or `cannedResume()` on blank input | `StubAiProvider.java:36-41` |
| `jd_parser` | `RawTextJobParser.parseJob(...)` (heuristic) or `cannedJob()` on blank input | `StubAiProvider.java:42-47` |
| `requirement_extraction` | `RawTextJobParser.parseRequirements(...)` — lifts `requirements` from existing JD JSON first, else line heuristics; or `cannedRequirements()` | `StubAiProvider.java:48-53`, `RawTextJobParser.java:75-151` |
| `resume_tailoring` | `cannedTailoring(...)` — deterministic grounded rewrite | `StubAiProvider.java:54`, `299-339` |
| `fact_validation` | `cannedFactValidation(...)` — grounded-token diff rule | `StubAiProvider.java:55`, `380-415` |
| anything else | Generic `{"_stub": true, "task": ...}` note | `StubAiProvider.java:56-58` |

Downstream consumers are tolerant of both stub JSON and real-provider output:
`BulletRewriter.extractText` parses `tailored_text` or raw text
(`BulletRewriter.java:77-96`); `ValidationIssueNormalizer` parses the stub's
fact-validation JSON.

---

## 2. Working (behavior per task)

- **resume_parser / jd_parser** — real heuristic parsing when user text is
  present; fixed fixtures only for blank input. The canned Jane Doe resume and
  Google JD are deliberately coherent with each other (Python/FastAPI/
  PostgreSQL/Redis) so the offline end-to-end demo flows.
  `RawTextResumeParser` uses regex for email/phone/linkedin/github
  (`RawTextResumeParser.java:21-24`), first-short-line name detection
  (`RawTextResumeParser.java:90-99`), section scanning for skills/experience/
  projects/education. `RawTextJobParser` detects title by role words,
  seniority by title keywords, company from a known-company table + suffix
  heuristics, requirements by bullet/prose heuristics
  (`RawTextJobParser.java:170-288`).
- **requirement_extraction** — `tryParseFromJdJson` reuses already-structured
  JD JSON and normalizes `type`/`importance` to DB CHECK-constraint values
  (`RawTextJobParser.java:111-151`); falls back to line heuristics and a fixed
  tech-stack list.
- **resume_tailoring** — `groundedKeyword()` picks the first JD keyword
  (stable order, ≥3 chars, not a stop word) that the resume evidence supports
  but the bullet omits (`StubAiProvider.java:321-339`), then appends
  ", with a focus on <keyword>." Facts (Category A) are never modified — only
  a focus clause is appended. Hard fallback: `"python"` if nothing matches.
  Tokenization mirrors `DeterministicValidator` (trailing-punctuation strip)
  so stub and deterministic tokens compare equal (`StubAiProvider.java:360-371`).
- **fact_validation** — reproduces the deterministic grounded-token rule in
  the AI-pipeline schema: tokens in the tailored text absent from
  original+evidence are flagged and typed `inflated_metric` (digit),
  `unsupported_technology` (`+ # .` shape), or `unsupported_descriptor`
  (`StubAiProvider.java:395-411`). Category-C claims always validate clean
  (the rule engine owns them).

---

## 3. Accuracy & limitations

### What the stub gets right

- **Deterministic and repeatable** — critical for CI. `SmokeTest`,
  `JobPipelineIntegrationTest`, `ResumePipelineIntegrationTest`,
  `TailoringPipelineIntegrationTest`, `AnalysisPipelineIntegrationTest`, and
  the `mvn verify` gate (248/248 tests) all rely on it.
- **Schema-valid output** — §4.2/§4.3-shaped, code-fence tolerant.
- **Tailoring is genuinely grounded** — evidence-supported keyword only,
  A/B/C-safe by construction; fact-validation mirrors the deterministic rule,
  so stub and real-provider outputs exercise the same validation path.
- **Zero latency, no keys, fully offline.**

### What it gets wrong / can't capture

- **Parser heuristics are shallow:**
  - Skills `years` hardcoded to `3` (`RawTextResumeParser.java:169`).
  - Bullet `technologies` / `metrics` / `domains` arrays emitted **empty**
    (`RawTextResumeParser.java:220-223`).
  - `isActionVerb` is a fixed 26-word list (`RawTextResumeParser.java:274-289`).
  - Name detection grabs the first short non-email line — can misfire on
    headers/addresses.
  - Projects/education parse only a single line each.
- **Canned fixtures are synthetic** — Jane Doe/Google are coherent but not
  derived from input; don't treat them as "AI output quality".
- **Fixed vocabularies** — `extractCompany` knows ~9 companies + suffix
  heuristics (`RawTextJobParser.java:213-239`); tech-keyword extraction is a
  fixed list (no NER). Unknown companies/tools silently come back empty —
  same limitation the deterministic validator documents
  (`DeterministicValidator.java:25-29`).
- **`groundedKeyword` fallback `return "python"`** (`StubAiProvider.java:338`)
  can fabricate a focus keyword unrelated to the bullet when evidence is
  sparse — the one place the stub can genuinely hallucinate.
- **Fact-validation is a heuristic** — over-reports on real text (any
  ungrounded word, including proper nouns, becomes an issue), under-reports on
  tokens < 3 chars or stop words, and its stop list is broader than
  `DeterministicValidator`'s FUNCTION_WORDS, so results can diverge slightly
  from the deterministic stage.

### Note on "accuracy"

There is **no measured accuracy score** — accuracy is structural: the stub is
correct by construction on its rules, and the parsers are best-effort
heuristics whose quality depends on input format. Green tests under stub mode
prove wiring and schema validity, never LLM output quality.

---

## 4. Risks & recommendations

1. **Stub is CI/demo-safe, not a quality proxy.** Do not tune prompts or eval
   expectations against stub output.
2. **Swap-in risk:** `AiFacade.activeProvider()` uses
   `providers.stream().findFirst()` (`AiFacade.java:138`) — fine today because
   exactly one provider bean exists per mode, but brittle if a second provider
   ever loads. Add `@Order`/primary qualifier if providers multiply.
3. **Variables-map contract:** the stub depends on `AiFacade` passing raw
   slots via `request.variables()`. If a future caller omits a slot (e.g.
   `jd_keywords`), `groundedKeyword` gets empty strings and silently returns
   `"python"` — a latent fabrication path.
4. **Real-fidelity path:** keep the stub for CI, but add a snapshot/eval
   harness comparing stub vs. omniroute output on a fixture corpus before
   relying on AI-specific features. Semantic matching is still a no-op stub
   too (`SemanticMatcher.java:11`).

---

## Appendix — files referenced

| Path | Role |
|------|------|
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/ai/StubAiProvider.java` | The stub itself |
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/ai/AiProvider.java` | Provider seam |
| `backend/src/main/java/com/atsdoctor/backend/application/ai/AiFacade.java` | Facade: retries/fallback/degradation/recording |
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/RawTextResumeParser.java` | Heuristic resume parser |
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/parsing/RawTextJobParser.java` | Heuristic JD parser + requirement extraction |
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/tailoring/BulletRewriter.java` | Tolerant output consumer |
| `backend/src/main/java/com/atsdoctor/backend/infrastructure/validation/DeterministicValidator.java` | Grounded-token rule the stub mirrors |
| `backend/src/test/java/com/atsdoctor/backend/StubAiProviderTest.java` | Stub behavior tests |
| `backend/src/test/java/com/atsdoctor/backend/AiLayerTest.java` | Facade retry/fallback/degradation tests |
