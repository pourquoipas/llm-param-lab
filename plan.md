# llm-param-lab — Development Plan

Build a Java 21 + Quarkus 3 + Langchain4j 1.x tool to test LLMs across prompts and
parameter combinations, with evaluation (expected output or judge LLM).

**Workflow rule:** implement ONE step at a time. Each step must compile and be
verified before the next. Wait for confirmation before starting a new step
(even in autopilot).

**Always follow `.github/copilot-instructions.md`** (non-negotiable):
- DOC: keep `docs/` current — `INDEX.md` (≤50 lines, `### trigger → path`),
  `project/conventions.md`, `project/base-classes.md`. Max 40 lines/file,
  one line per entry, `→` lists, no markdown tables, no prose openers.
  Create a new doc file when a pattern/convention is not yet documented;
  update (never rewrite) existing ones. Code wins over docs.
- UI: very minimal + futuristic — minimize user work/choices, drive their work.
- Tests: write and run tests after every modification.
- Docs: document project/classes/behaviours with `.md` files.
- `README.md`: always kept up to date with an overview.
- License: CC BY-NC-SA 4.0 (see `LICENSE`).

---

## Step 1 — Maven project scaffold
**Goal:** a runnable Quarkus app with all dependencies declared.

- [ ] `pom.xml` with:
  - Java 21, quarkus-bom 3.15+ (parent `quarkus-bom` import)
  - `quarkus-rest`
  - `quarkus-hibernate-orm-h2` (runtime + compile)
  - `quarkus-liquibase`
  - `quarkus-jackson`
  - langchain4j 1.x: `langchain4j`, `langchain4j-open-ai`, `langchain4j-ollama`
  - `snakeyaml` (Quarkus pulls it in, but declare explicitly for config loading)
- [ ] `src/main/java/com/example/llmlab/LlmParamLabApp.java` — minimal `@ApplicationScoped` class + a tiny `/api/ping` resource returning `"pong"` (temp, removed later) — just to prove the app boots.
- [x] `src/main/resources/application.yml` — minimal (datasource/h2 file in `./data/llmlab`, `hibernate.ddl-auto: none`)

Notes from implementation:
- `quarkus-logging-jul` does not exist on Maven Central → omitted (Quarkus uses JBoss LogManager natively).
- System Maven is 3.6.3 (too old for Quarkus 3.15, needs ≥3.8.6) → added **Maven Wrapper** `./mvnw` (Maven 3.9.9). Use `./mvnw quarkus:dev`.
- Quarkus 3.x needs the `quarkus-config-yaml` extension to parse `application.yml` (added).

**Verify (done):** `./mvnw quarkus:dev` boots (Quarkus 3.15.1, ~2s); `curl http://localhost:8080/api/ping` → `"pong"`.

---

## Step 2 — Liquibase schema
**Goal:** all tables created by Liquibase on startup (no Hibernate DDL).

- [x] `application.yml` final datasource + liquibase config (see corrected keys below)
- [x] `src/main/resources/db/changelog/db.changelog-master.yaml` — includes `changes/001-initial-schema.yaml`
- [x] `src/main/resources/db/changelog/changes/001-initial-schema.yaml` — creates in FK order:
  `model_config`, `test_suite`, `test_case`, `param_sweep`, `run_result`
  (exact columns per prompt.txt §3; CLOB for big text; `ON DELETE CASCADE` where specified)

Notes from implementation (prompt.txt had wrong property names):
- Correct Quarkus keys: `quarkus.datasource.jdbc.url` (nested under `jdbc`),
  `quarkus.hibernate.orm.ddl-auto`, `quarkus.liquibase.change-log` (NOT `changelog`/`changelog-file`).
- `quarkus.liquibase.migrate-at-start: true` is REQUIRED — without it Liquibase loads the
  changelog but runs no changesets (silent no-op).
- `quarkus.datasource.devservices.enabled: false` — stops Dev Services swapping the file H2 for in-memory.
- Liquibase YAML: use **block-style** columns (`- column:` …), not flow-style inline `{}` maps —
  flow-style triggers `ParsedNodeException: Multiple nodes match null/name`.

**Verify (done):** `./mvnw quarkus:dev` boots; `./data/llmlab.mv.db` created; all 5 tables exist
(`MODEL_CONFIG, PARAM_SWEEP, RUN_RESULT, TEST_CASE, TEST_SUITE` + Liquibase tracking tables).
Clean re-creation tested (delete `./data/` → restart → tables recreated).
Idempotency tested (restart without delete → "Database is up to date, no changesets to execute").

**Rule:** never edit `001-initial-schema.yaml` again — future changes are new files.

---

## Step 3 — Domain entities + repositories
**Goal:** persistence layer works.

- [x] Entities in `domain/` mapped to the Liquibase schema (names match exactly):
  - `ModelConfig` (provider enum `ModelProvider { OLLAMA, OPENAI_COMPATIBLE }`)
  - `TestSuite` (expectedOutputMode enum `ExpectedOutputMode { EXACT, CONTAINS, REGEX, NONE }`, FK `judgeModelId`)
  - `TestCase` (nullable `systemPrompt`)
  - `ParamSweep`
  - `RunResult` (evaluationType enum `EvaluationType { EXACT_MATCH, CONTAINS, REGEX_MATCH, JUDGE_LLM, SKIPPED }`)
- [x] Repositories in `repository/` (EntityManager-based, `@ApplicationScoped`): find/save/delete +
  helpers (e.g. `TestSuiteRepository.findByJudgeModelId`, `ModelConfigRepository.findActive()`).
- [x] Smoke check: `EntitySmokeTest` (@QuarkusTest) persists + reads one row of each entity.

Notes from implementation:
- `values` is a **reserved keyword in H2** → the `param_sweep.values` column broke INSERTs.
  Fixed via new changeset `002-rename-values-column.yaml` (rename to `param_values`); entity maps
  `values` field → `param_values` column. (Follows the "never edit 001, add new file" rule.)
- Repositories use `EntityManager` + `@Transactional` on writes (portable JPA, no Hibernate-specific API).
- `TestSuite`/`RunResult` set `createdAt`/`updatedAt` via `@PrePersist`/`@PreUpdate`.
- Test config `src/test/resources/application.yml` uses in-memory H2 so tests never touch `./data/`.

**Verify (done):** `./mvnw test` → `EntitySmokeTest` passes (persist + read each entity, FKs + timestamps OK).
Dev app boots against file DB; 002 rename applied as a real migration ("Column param_sweep.values renamed to param_values").

---

## Step 4 — ModelFactory
**Goal:** `ModelFactory` service returning a langchain4j `ChatModel` per `ModelConfig`.

- [x] `config/ModelFactory.java`:
  - `OLLAMA` → `OllamaChatModel.builder().baseUrl(...).modelName(...).build()`
  - `OPENAI_COMPATIBLE` → `OpenAiChatModel.builder().baseUrl(...).apiKey(...).modelName(...).build()`
  - **No temperature/topP/maxTokens in builder** — those go in `ChatRequestParameters` at call time.
- [x] `config/AppConfig.java` — reads `app.default-judge-prompt` from yml (`@ConfigProperty`, empty default).

Notes from implementation:
- langchain4j **1.0.1** renamed the interface: it is `dev.langchain4j.model.chat.ChatModel`
  (the 0.x `ChatLanguageModel` does NOT exist in 1.0.1). `ModelFactory.create()` returns `ChatModel`.
- BOM 1.0.1 pins `langchain4j-ollama` → `1.0.1-beta6` (open-ai/core are `1.0.1`). Both model
  classes implement `ChatModel`; builders accept `baseUrl`/`modelName` (+`apiKey` for OpenAI).
- `OpenAiChatModel` builds fine with a **null apiKey** (local OpenAI-compatible servers) — no validation.

**Verify (done):** `./mvnw test` → `ModelFactoryTest` (3 tests) passes: builds an Ollama model,
builds an OpenAI-compatible model with null apiKey, and `AppConfig` is injectable. No real API call.

---

## Step 5 — TestRunnerService (core run logic)
**Goal:** `runSuite(Long suiteId)` executes the full sweep and evaluates.

- [x] `service/TestRunnerService.java`:
  1. Load suite + test cases + param sweeps + active model config.
  2. Cartesian product of sweep values (e.g. temp × topP).
  3. For each testCase × each combo:
     - Build messages: SystemMessage only if `testCase.systemPrompt` non-null/non-empty, then UserMessage.
     - `ChatRequest` with `ChatRequestParameters` (temperature/topP/maxTokens from combo).
     - Measure latency (`System.nanoTime()`), capture `TokenUsage` (nullable) from response metadata.
     - Evaluate:
       - expectedOutput set → EXACT (trim + equals) / CONTAINS (case-insensitive) / REGEX → score 1.0/0.0
       - else judgeModelId set → judge call (Step 6)
       - else → SKIPPED
     - Persist `RunResult`.
  4. Concurrency guard: `ConcurrentHashMap<Long, Boolean>` lock, max 1 concurrent run per suite.
- [x] `runAll()` iterating all suites.

Notes from implementation:
- Pure, unit-testable core: `cartesianProduct(List<ParamSweep>)` and the evaluators
  (`evaluate`, `evaluateExpected`) are public and DB/model-free. `Evaluation` is a nested record.
- `paramName` → builder mapping: `temperature`→`.temperature`, `topP`→`.topP`,
  `maxTokens`→`.maxOutputTokens` (note the name difference). Unknown names ignored.
- No sweeps → single empty combo (one run per test case, no params).
- Judge path is a **stub** (`evaluateWithJudge` returns JUDGE_LLM, score=null) — real impl in Step 6.
- `SuiteAlreadyRunningException` (new) for the concurrency guard → mapped to 409 in Step 9.

**Verify (done):** `./mvnw test` → `TestRunnerServiceTest` (7 tests) passes: cartesian product
(3×2=6, empty→1), EXACT/CONTAINS/REGEX pass+fail, SKIPPED, judge-stub type. No DB, no model call.

---

## Step 6 — Judge LLM evaluation
**Goal:** judge path produces `score` (0.0–1.0) + `scoreReason`.

- [ ] In `TestRunnerService`, when no expectedOutput:
  - Judge prompt = `suite.judgePrompt` or `app.default-judge-prompt` from config.
  - Replace `{{task}}`, `{{expected}}` (or `"N/A"`), `{{response}}`.
  - Call judge model (temperature 0, no params sweep).
  - Parse JSON leniently: strip markdown code fences → trim → Jackson parse into `JudgeResponse { score, reason }`.
  - Parse failure → `score = null`, `scoreReason = "judge parse error: ..."`.

**Verify:** compiles; test the JSON-stripping + parsing with sample LLM replies (```json fences, extra text).

---

## Step 7 — REST API: Models
**Goal:** full model management.

- [ ] `rest/ModelConfigResource.java` (`/api/models`):
  - `GET /` — list all
  - `POST /` — create
  - `PUT /{id}` — update
  - `DELETE /{id}`
  - `POST /{id}/activate` — sets isActive=true, others false (transactional)
- [ ] `service/ModelConfigService.java` — validation (unique name, required fields) + activate logic.

**Verify:** `mvn quarkus:dev` + curl all endpoints against H2; confirm activate flips flags.

---

## Step 8 — REST API: Suites
**Goal:** suite CRUD with nested testCases[] and paramSweeps[].

- [ ] `dto/SuiteCreateRequest.java` — nested `List<TestCaseDto>`, `List<ParamSweepDto>`
- [ ] `dto/SuiteResponse.java` — includes testCases, paramSweeps, latest results summary
- [ ] `rest/TestSuiteResource.java` (`/api/suites`):
  - `GET /`, `POST /` (nested create), `GET /{id}`, `PUT /{id}`, `DELETE /{id}`
- [ ] `service/TestSuiteService.java` — cascade create/replace testCases + paramSweeps,
  validate `expectedOutputMode` vs `expectedOutput`/`judgeModelId` consistency,
  validate `paramName` is one of temperature/topP/maxTokens, `values` is valid JSON array.

**Verify:** curl round-trip: create suite with 2 cases + 2 sweeps → GET returns identical data.

---

## Step 9 — REST API: Run + Results
**Goal:** trigger runs and query results.

- [ ] `rest/ResultResource.java`:
  - `POST /api/suites/{id}/run` → synchronous `runSuite`, returns `List<RunResult>` (409 if already running)
  - `POST /api/run-all` → `runAll()`
  - `GET /api/results?suiteId=&testCaseId=` — filter
  - `GET /api/results/summary?suiteId=` → `RunSummaryResponse`: per test case, best param combo (highest avg score), table of all combos with avg score + min/max latency
- [ ] `dto/RunSummaryResponse.java`

**Verify:** with a fake/offline scenario — at minimum: no-active-model and no-suite error paths (4xx),
and a mock-free check that endpoints exist and return 200/4xx correctly. Real run verified in Step 15 if LLM available.

---

## Step 10 — Seed data
**Goal:** example data on first start (only if DB empty).

- [ ] `@Observes StartupEvent` (or `ApplicationScoped` `@Observes @Startup`):
  - If `model_config` table empty → create:
    - "local-llama" (OLLAMA, `http://localhost:11434`, `llama3.1`)
    - "remote-gpt" (OPENAI_COMPATIBLE, `https://api.openai.com/v1`, `gpt-4o-mini`)
  - If `test_suite` empty → create "JSON extraction test" suite with 3 test cases
    (each with its OWN systemPrompt) + 2 sweeps (temperature [0.0, 0.3, 0.7], topP [0.9, 0.95]),
    judgeModelId → remote-gpt.

**Verify:** delete `./data/`, restart, check tables seeded (curl API or H2). Restart again → no duplicates.

---

## Step 11 — Frontend: layout + theme
**Goal:** shell of the SPA.

- [ ] `META-INF/resources/index.html` — top bar (title + active model indicator), 3 tabs (Models/Suites/Results), content sections, `<template>`-based modals for forms, toast container, spinner.
- [ ] `META-INF/resources/styles.css` — dark theme per prompt.txt §8 (colors, striped tables, pill badges, modal with backdrop blur, CSS spinner, pre blocks max-height 300px).
- [ ] `META-INF/resources/app.js` — skeleton: `state` object, `init()`, tab switching, `api()` fetch wrapper (error toast on non-2xx), toast helper, render helpers.

**Verify:** `mvn quarkus:dev` → open `http://localhost:8080/` — tabs switch, styling correct, no console errors (API calls will 404/500 until APIs done — stub data OK).

---

## Step 12 — Frontend: Models tab
**Goal:** full model CRUD in UI.

- [ ] Table (Name, Provider, Base URL, Model, Active badge, Actions)
- [ ] Add/Edit modal (name, provider select, baseUrl, apiKey, modelName)
- [ ] Delete (confirm), Activate (radio-style) → re-fetch, update top-bar indicator.

**Verify:** create/edit/delete/activate models via UI; top bar shows active model.

---

## Step 13 — Frontend: Suites tab
**Goal:** full suite editing.

- [ ] Left: suite list + "New Suite". Right: detail editor.
- [ ] Suite fields: name, description, expectedOutput (textarea), expectedOutputMode (select), judgeModel (select), judgePrompt (textarea).
- [ ] TestCases section: add/edit/delete rows (name, systemPrompt textarea w/ placeholder, userPrompt textarea, sortOrder).
- [ ] ParamSweeps section: add/edit/delete rows (paramName, comma-separated values input → JSON array).
- [ ] "Save Suite" (POST new / PUT existing), "Run Suite" (POST run, disable button + spinner, toast "Run complete: N results", auto-switch to Results), "Delete Suite" (confirm).

**Verify:** create the example suite via UI, save, re-open (data intact), delete.

---

## Step 14 — Frontend: Results tab
**Goal:** browse + run results.

- [ ] Filter bar: suite dropdown + optional test case filter.
- [ ] Summary card: best param combo for selected suite (from `/api/results/summary`).
- [ ] Results table: Test Case | Params | Score (colored pill) | Passed | Latency | Tokens In/Out | Eval Type | Date.
- [ ] Row click → expand: `rawOutput` in `<pre>`, `scoreReason`.
- [ ] "Run All" button (top right, spinner while running).

**Verify:** after a run (real or seeded), results render, filtering works, row expansion works.

---

## Step 15 — README + end-to-end verification
**Goal:** documented, fully working tool.

- [ ] `README.md` per prompt.txt §12 (what/prereqs/quick start/schema versioning/API reference w/ curl examples).
- [ ] Remove the temp `/api/ping` endpoint from Step 1 (and any other temp code).
- [ ] Final E2E: clean `./data/`, `mvn quarkus:dev`, verify: seed data present, UI works,
  run a suite against Ollama (if running locally) — check results + scores in UI.
  (If no local LLM: verify error handling shows clean 4xx/toasts instead of stack traces.)
- [ ] `mvn clean package` succeeds (non-dev build).

**Verify:** checklist above all green; `mvn clean package` + `java -jar` boots.

---

## Step dependency graph

```
1 (scaffold) → 2 (liquibase) → 3 (entities) → 4 (model factory)
4 → 5 (runner core) → 6 (judge)
3 → 7 (models API) → 8 (suites API) → 9 (run/results API)
3 → 10 (seed data)
9 → 11 → 12 → 13 → 14 (frontend, sequential)
All → 15 (README + E2E)
```

Steps 5–6 and 7–8 can proceed in parallel if desired, but one-at-a-time is the rule.

## Status tracker

| Step | Name | Status |
|------|------|--------|
| 1 | Maven scaffold | ✅ done |
| 2 | Liquibase schema | ✅ done |
| 3 | Entities + repositories | ✅ done |
| 4 | ModelFactory | ✅ done |
| 5 | TestRunnerService core | ✅ done |
| 6 | Judge evaluation | ⬜ not started |
| 7 | REST: Models | ⬜ not started |
| 8 | REST: Suites | ⬜ not started |
| 9 | REST: Run + Results | ⬜ not started |
| 10 | Seed data | ⬜ not started |
| 11 | Frontend: layout + theme | ⬜ not started |
| 12 | Frontend: Models tab | ⬜ not started |
| 13 | Frontend: Suites tab | ⬜ not started |
| 14 | Frontend: Results tab | ⬜ not started |
| 15 | README + E2E | ⬜ not started |
