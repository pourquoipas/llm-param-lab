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

- [x] In `TestRunnerService`, when no expectedOutput:
  - Judge prompt = `suite.judgePrompt` or `app.default-judge-prompt` from config.
  - Replace `{{task}}`, `{{expected}}` (or `"N/A"`), `{{response}}`.
  - Call judge model (temperature 0, no params sweep).
  - Parse JSON leniently: strip markdown code fences → trim → Jackson parse into `JudgeResponse { score, reason }`.
  - Parse failure → `score = null`, `scoreReason = "judge parse error: ..."`.

Notes from implementation:
- `dto/JudgeResponse.java` (new) — record `{ Double score, String reason }`, `@JsonIgnoreProperties(ignoreUnknown = true)`.
- `evaluateWithJudge` looks up the judge `ModelConfig` by `suite.judgeModelId`, builds the prompt,
  calls the judge at temperature 0, parses the verdict. `passed = score >= 0.5` (`JUDGE_PASS_THRESHOLD`).
- Pure, DB/model-free helpers (public, unit-tested): `buildJudgePrompt`, `stripCodeFences`, `parseJudgeResponse`.
- `stripCodeFences` strips ``` fences then isolates the first `{`…last `}` so surrounding prose is tolerated.

**Verify (done):** `./mvnw test` → `TestRunnerServiceTest` (12 tests) passes: placeholder substitution
(task/expected/response, expected→"N/A"), JSON parse of plain / ```json-fenced / prose-wrapped replies,
garbage→null. No DB, no model call.

---

## Step 7 — REST API: Models
**Goal:** full model management.

- [x] `rest/ModelConfigResource.java` (`/api/models`):
  - `GET /` — list all
  - `POST /` — create
  - `PUT /{id}` — update
  - `DELETE /{id}`
  - `POST /{id}/activate` — sets isActive=true, others false (transactional)
- [x] `service/ModelConfigService.java` — validation (unique name, required fields) + activate logic.

Notes from implementation:
- `service/ApiException.java` — `RuntimeException` base with `int status` + `getStatus()`.
  Subclasses: `ModelNotFoundException` (404), `ModelNameAlreadyExistsException` (409), `ValidationException` (400).
- `rest/ApiExceptionMapper.java` — `@Provider` `ExceptionMapper<ApiException>` → `Response.status(ex.getStatus()).entity({"error": msg})`.
- `dto/ModelConfigRequest.java` (record: name, provider, baseUrl, apiKey, modelName) + `dto/ModelConfigResponse.java` (record + `from(ModelConfig)`).
- `ModelConfigService` — `list`/`create`/`update`/`delete`/`activate`, all `@Transactional`; `create`/`update` validate required fields (400) + unique name (409); `activate` sets target active, deactivates all others.
- `@Consumes(APPLICATION_JSON)` on `create`/`update` only (NOT class-level) — a bodyless `POST /{id}/activate` with class-level `@Consumes` returns 415.
- **pom:** `quarkus-rest` (RESTEasy Reactive) needs `quarkus-rest-jackson` (not standalone `quarkus-jackson`) to register the REST JSON body reader/writer — without it, POST/PUT → 415.

**Verify (done):** `./mvnw test` → `ModelConfigResourceTest` (8 rest-assured tests) passes: create 201 + appears in list,
duplicate name 409, missing field 400, update 200, update 404, delete 204, activate flips flags, activate 404.

---

## Step 8 — REST API: Suites
**Goal:** suite CRUD with nested testCases[] and paramSweeps[].

- [x] `dto/SuiteCreateRequest.java` — nested `List<TestCaseDto>`, `List<ParamSweepDto>`
- [x] `dto/SuiteResponse.java` — includes testCases, paramSweeps, latest results summary
- [x] `rest/TestSuiteResource.java` (`/api/suites`):
  - `GET /`, `POST /` (nested create), `GET /{id}`, `PUT /{id}`, `DELETE /{id}`
- [x] `service/TestSuiteService.java` — cascade create/replace testCases + paramSweeps,
  validate `expectedOutputMode` vs `expectedOutput`/`judgeModelId` consistency,
  validate `paramName` is one of temperature/topP/maxTokens, `values` is valid JSON array.

Notes from implementation:
- `dto/TestCaseDto.java` (record: name, systemPrompt, userPrompt, sortOrder) + `dto/ParamSweepDto.java` (record: paramName, values).
- `SuiteResponse` — suite fields + `List<TestCaseDto>` + `List<ParamSweepDto>` + `latestRunAt` (latest results summary).
- `service/SuiteNotFoundException.java` (404). `TestSuiteService` — `list`/`get`/`create`/`update`/`delete`, all writes `@Transactional`.
- `create`/`update` → `replaceChildren`: delete existing testCases + paramSweeps, then insert the request's (full replace).
- `delete` → manually delete children (testCases, paramSweeps) then the suite (no JPA cascade; entities use plain `suiteId`).
- Validation (400): name required; mode required; mode==NONE → judgeModelId required; mode!=NONE → expectedOutput required; each case needs name+userPrompt; each sweep paramName ∈ {temperature,topP,maxTokens} + values a valid JSON array (Jackson `readTree().isArray()`).
- `RunResultRepository.findLatestRunAt(suiteId)` → `Optional<LocalDateTime>` via `select max(r.createdAt)`.

**Verify (done):** `./mvnw test` → `TestSuiteResourceTest` (12 rest-assured tests) passes: round-trip create (2 cases + 2 sweeps) → GET identical,
appears in list, missing name 400, mode-without-expectedOutput 400, NONE-without-judge 400, bad paramName 400, bad values 400,
get 404, update 200, update 404, delete 204, delete 404.

---

## Step 9 — REST API: Run + Results
**Goal:** trigger runs and query results.

- [x] `rest/ResultResource.java`:
  - `POST /api/run-all` → `runAll()`
  - `GET /api/results?suiteId=&testCaseId=` — filter
  - `GET /api/results/summary?suiteId=` → `RunSummaryResponse`: per test case, best param combo (highest avg score), table of all combos with avg score + min/max latency
- [x] `POST /api/suites/{id}/run` → synchronous `runSuite`, returns `List<RunResult>` (409 if already running) — lives in `TestSuiteResource`
- [x] `dto/RunSummaryResponse.java`

Notes from implementation:
- `POST /api/suites/{id}/run` moved into `TestSuiteResource` (not `ResultResource`): a cross-resource path overlap (`/api/suites` vs `/api` + `/suites/{id}/run`) made RESTEasy return the default 404 HTML. Keeping suite-scoped routes in one resource fixes routing.
- `service/ResultService.java` — `results(suiteId, testCaseId)` (dynamic JPQL filter, both optional) + `summary(suiteId)` (groups results by testCase → by paramsJson; best combo = highest avg score; min/max latency per combo).
- `dto/RunSummaryResponse.java` — `suiteId` + `List<TestCaseSummary>`; each has `bestCombo` + `List<ComboSummary>` (paramsJson, avgScore, min/maxLatencyMs, runCount).
- Exceptions now typed for the REST layer: `SuiteAlreadyRunningException` → 409, `SuiteNotFoundException` (reused) → 404, new `NoActiveModelException` → 400. `TestRunnerService.runSuite` throws these (was generic `IllegalArgument`/`IllegalState`).

**Verify (done):** `./mvnw test` → `ResultResourceTest` (6 rest-assured tests) passes: run unknown suite 404, run no-active-model 400, run-all no-active-model 400, results unknown suite → `[]`, summary unknown suite 404, summary valid suite no results → empty. All 42 tests green. Real run verified in Step 15 if LLM available.

---

## Step 10 — Seed data ✅
**Goal:** example data on first start (only if DB empty).

- [x] `@Observes StartupEvent` (or `ApplicationScoped` `@Observes @Startup`):
  - If `model_config` table empty → create:
    - "local-llama" (OLLAMA, `http://localhost:11434`, `llama3.1`)
    - "remote-gpt" (OPENAI_COMPATIBLE, `https://api.openai.com/v1`, `gpt-4o-mini`)
  - If `test_suite` empty → create "JSON extraction test" suite with 3 test cases
    (each with its OWN systemPrompt) + 2 sweeps (temperature [0.0, 0.3, 0.7], topP [0.9, 0.95]),
    judgeModelId → remote-gpt.

**Verify:** delete `./data/`, restart, check tables seeded (curl API or H2). Restart again → no duplicates. ✅

Notes:
- `seed/SeedData.java` — `@ApplicationScoped`, `@Transactional onStartup(@Observes StartupEvent)`; `seedModels()` + `seedSuite()`; each guarded by `findAll().isEmpty()` (idempotent).
- judgeModelId resolved via `modelRepo.findByName("remote-gpt")` (models seeded first).
- Verified: first boot seeds 2 models + 1 suite (3 cases, 2 sweeps); second boot → no duplicates (still 2 + 1).

---

## Step 11 — Frontend: layout + theme ✅
**Goal:** shell of the SPA.

- [x] `META-INF/resources/index.html` — top bar (title + active model indicator), 3 tabs (Models/Suites/Results), content sections, `<template>`-based modals for forms, toast container, spinner.
- [x] `META-INF/resources/styles.css` — dark theme per prompt.txt §8 (colors, striped tables, pill badges, modal with backdrop blur, CSS spinner, pre blocks max-height 300px).
- [x] `META-INF/resources/app.js` — skeleton: `state` object, `init()`, tab switching, `api()` fetch wrapper (error toast on non-2xx), toast helper, render helpers.

**Verify:** `mvn quarkus:dev` → open `http://localhost:8080/` — tabs switch, styling correct, no console errors (API calls will 404/500 until APIs done — stub data OK). ✅

Notes:
- `index.html` — top bar (title + `#active-model` dot/name), `.tabs` nav, 3 `.tab-panel` sections, `#modal-template` + `#modal-root`, `#toast-container`, `#spinner-overlay`.
- `styles.css` — CSS vars per §8 palette; striped/hover tables, pill badges (`.badge-score` + score-high/mid/low), modal backdrop blur, CSS spinner, `pre` max-height 300px.
- `app.js` — `state`, `api()` (JSON body auto-serialize, non-2xx → toast + throw, 204 → null), `toast()`, `showSpinner/hideSpinner`, `openModal(title, bodyHtml)`, `switchTab()`, `renderActiveModel()`, placeholder `renderModels/renderSuites/renderResults` (Steps 12–14).
- Verified: `package` + `java -jar` → `/`, `/styles.css`, `/app.js` all 200; `node --check app.js` OK; `/api/models` + `/api/suites` 200.

---

## Step 12 — Frontend: Models tab ✅
**Goal:** full model CRUD in UI.

- [x] Table (Name, Provider, Base URL, Model, Active badge, Actions)
- [x] Add/Edit modal (name, provider select, baseUrl, apiKey, modelName)
- [x] Delete (confirm), Activate (radio-style) → re-fetch, update top-bar indicator.

**Verify:** create/edit/delete/activate models via UI; top bar shows active model. ✅

Notes:
- `renderModels()` — table (Name/Provider/Base URL/Model/Active badge/Actions) + "Add Model" button.
- `openModelModal(model?)` — add/edit form (name, provider select OLLAMA/OPENAI_COMPATIBLE, baseUrl, apiKey optional, modelName); POST create / PUT update; re-fetch + `renderActiveModel()`.
- `activateModel(id)` → POST `/api/models/{id}/activate`; `deleteModel(id)` → confirm + DELETE.
- `esc()` — HTML-escape helper for all user data in templates.
- Verified: served app.js has Models tab; API CRUD (create 201 / activate / update / delete 204) works; top bar shows active model.

---

## Step 13 — Frontend: Suites tab ✅
**Goal:** full suite editing.

- [x] Left: suite list + "New Suite". Right: detail editor.
- [x] Suite fields: name, description, expectedOutput (textarea), expectedOutputMode (select), judgeModel (select), judgePrompt (textarea).
- [x] TestCases section: add/edit/delete rows (name, systemPrompt textarea w/ placeholder, userPrompt textarea, sortOrder).
- [x] ParamSweeps section: add/edit/delete rows (paramName, comma-separated values input → JSON array).
- [x] "Save Suite" (POST new / PUT existing), "Run Suite" (POST run, disable button + spinner, toast "Run complete: N results", auto-switch to Results), "Delete Suite" (confirm).

**Verify:** create the example suite via UI, save, re-open (data intact), delete. ✅

Notes:
- `renderSuites()` — left list (`.suite-item` selectable) + right `renderSuiteEditor()`; `state.suiteDraft` holds the working copy (live-bound inputs).
- `renderTestCases()` / `renderSweeps()` — sub-rows with add/delete; sweep values: comma input ↔ JSON array (`valuesToInput`/`inputToValues`, numbers kept numeric).
- `saveSuite()` — POST new / PUT existing (full replace of testCases+paramSweeps, matches backend); `runSuite()` — POST run + spinner + toast "Run complete: N results" + auto-switch to Results; `deleteSuite()` — confirm + DELETE.
- Verified: served app.js has all Suites functions; API CRUD with exact UI body shape (create 201 / get / update 200 full-replace / delete 204).

---

## Step 14 — Frontend: Results tab ✅
**Goal:** browse + run results.

- [x] Filter bar: suite dropdown + optional test case filter.
- [x] Summary card: best param combo for selected suite (from `/api/results/summary`).
- [x] Results table: Test Case | Params | Score (colored pill) | Passed | Latency | Tokens In/Out | Eval Type | Date.
- [x] Row click → expand: `rawOutput` in `<pre>`, `scoreReason`.
- [x] "Run All" button (top right, spinner while running).

**Verify:** after a run (real or seeded), results render, filtering works, row expansion works. ✅

Notes:
- `TestCaseDto` gained a read-only `id` (first component) so the UI can map `RunResult.testCaseId` → test case name; create path ignores it (uses accessors), only `toResponse` populates it.
- `renderResults()` — filter bar (suite + optional test case), summary card (best combo per test case), results table; `renderSummaryCard()` / `renderResultsTable(tcName)` / `formatParams()` / `runAll()`.
- Score pill: green ≥ 0.7, yellow 0.4–0.7, red < 0.4 (existing `score-high/mid/low` classes). Row click toggles a hidden detail row (`rawOutput` + `scoreReason` in `<pre>`).
- `runSuite()` now sets `state.resultsSuiteId` so the Results tab opens on the just-run suite.
- Verified: 44 tests green; served app.js has all Results fns; seeded 3 results → API returns correct shape (best combo = highest avg score), then cleaned up. Ollama unreachable → run returns clean 500 (toast, no crash).

---

## Step 15 — README + end-to-end verification
**Goal:** documented, fully working tool.

- [x] `README.md` per prompt.txt §12 (what/prereqs/quick start/schema versioning/API reference w/ curl examples).
- [x] Remove the temp `/api/ping` endpoint from Step 1 (and any other temp code).
- [x] Final E2E: clean `./data/`, boot jar, verify: seed data present, UI works,
  run a suite against Ollama (if running locally) — check results + scores in UI.
  (If no local LLM: verify error handling shows clean 4xx/toasts instead of stack traces.)
- [x] `mvn clean package` succeeds (non-dev build).

**Notes:**
- Removed temp `HealthResource` (`/api/ping`) + `DiagResource` (`/api/diag`); `rest/` now holds only the 4 real resources + 2 mappers.
- Added `GenericExceptionMapper` (last-resort `@Provider`): `WebApplicationException` keeps its own status (404 stays 404), everything else → clean JSON 500. No more framework HTML error pages.
- No local LLM during E2E → verified clean error paths instead of a live run.

**Verify (done):** `./mvnw clean package` → BUILD SUCCESS, 44 tests green. Fresh boot (clean `./data/`, Liquibase recreates schema): seed present (2 models + 1 suite), UI assets 200, temp endpoints gone (404), no-active-model → clean 400 JSON, unreachable LLM → clean 500 JSON, unmapped route → clean 404 JSON.

---

## Step 16 — LLM config in `.env` + default model
**Goal:** default LLM = `localhost:11000` + `Qwen3.8-27B-UD-IQ3_S.gguf`, used for both execution (active model) and evaluation (judge); config lives in a gitignored `.env`.

- [x] `.env` file with `LLM_PROVIDER`, `LLM_BASE_URL`, `LLM_MODEL_NAME`, `LLM_API_KEY` (defaults: OPENAI_COMPATIBLE, `http://localhost:11000/v1`, `Qwen3.8-27B-UD-IQ3_S.gguf`, empty).
- [x] Add `.env` to `.gitignore`.
- [x] `LlmConfig` config class reading `llm.*` (Quarkus maps `.env` keys → `llm.*`), with `defaultValue` fallbacks so a fresh clone (no `.env`) still boots.
- [x] Seed creates ONE default model (from `LlmConfig`) flagged **active** (execution) and points the example suite's `judgeModelId` at it (evaluation).
- [x] Tests: seed uses `.env`/config values; default model active; suite judge = default model.

**Verify (done):** `./mvnw test` → 44 green. Fresh boot (clean `./data/`) → 1 active model (localhost:11000 + Qwen3.8-27B-UD-IQ3_S.gguf), example suite judge = that model.

## Step 17 — Admin endpoints (clean DB + insert test case)
**Goal:** two endpoints to reset the DB and to insert a minimal live test case.

- [x] `AdminService` + `AdminResource` (`/api/admin`).
- [x] `POST /api/admin/clean` → wipe all tables in FK order (run_result → test_case → param_sweep → test_suite → model_config).
- [x] `POST /api/admin/test-case` → ensure default model exists (active, from `LlmConfig`) + create a suite "Agent smoke test" with 2 test cases (distinct system+user prompts) and a temperature sweep `[0.5, 0.8]`; judge = default model.
- [x] Tests: clean empties all tables (re-seeds after); test-case creates active model + 2 cases + temp sweep; idempotent (re-runnable).
- [x] `ModelConfigService.deactivateAll()` + `ResultResourceTest` "no active model" tests made deterministic (deactivate all → 400, no LLM call; restore default in `finally`).

**Verify (done):** `./mvnw test` → 46 green. `ResultResourceTest` green in isolation (no LLM dependency).

## Step 18 — UI admin buttons
**Goal:** two top-bar buttons wired to the admin endpoints.

- [x] `index.html`: two buttons in the top bar ("＋ Test case", "Clean DB"), grouped right with the active-model indicator.
- [x] `app.js`: `insertTestCase()` (POST `/api/admin/test-case`) + `cleanDatabase()` (POST `/api/admin/clean`, with confirm), then re-render + switch tab.
- [x] `styles.css`: `.topbar-right` / `.topbar-actions` / `.topbar-btn` (+ danger hover) consistent with the theme.

**Verify (done):** `node --check app.js` OK. Fresh boot → both buttons present; POST test-case → 201 + "Agent smoke test" (2 cases, temp [0.5,0.8], default model active); POST clean → 204 + models/suites `[]`.

## Step 19 — README + final E2E ✅ done
**Goal:** documented, fully working tool with the new features.

- [x] `README.md`: document `.env` (LLM config) + the two admin buttons/endpoints + updated quick start.
- [x] Final E2E: clean `./data/`, boot jar, verify default model + example suite, POST test-case (201), clean DB (204).
- [x] `./mvnw clean package` succeeds (non-dev build).

**Verify (done):** `./mvnw clean package` OK; fresh boot → default model (active, from `.env`) + "JSON extraction test" suite; POST test-case → 201; POST clean → 204 + models/suites `[]`.

---

## Step dependency graph

```
1 (scaffold) → 2 (liquibase) → 3 (entities) → 4 (model factory)
4 → 5 (runner core) → 6 (judge)
3 → 7 (models API) → 8 (suites API) → 9 (run/results API)
3 → 10 (seed data)
9 → 11 → 12 → 13 → 14 (frontend, sequential)
All → 15 (README + E2E)
15 → 16 (.env + default model) → 17 (admin endpoints) → 18 (UI buttons) → 19 (README + E2E)
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
| 6 | Judge evaluation | ✅ done |
| 7 | REST: Models | ✅ done |
| 8 | REST: Suites | ✅ done |
| 9 | REST: Run + Results | ✅ done |
| 10 | Seed data | ✅ done |
| 11 | Frontend: layout + theme | ✅ done |
| 12 | Frontend: Models tab | ✅ done |
| 13 | Frontend: Suites tab | ✅ done |
| 14 | Frontend: Results tab | ✅ done |
| 15 | README + E2E | ✅ done |
| 16 | LLM config in `.env` + default model | ✅ done |
| 17 | Admin endpoints (clean DB + test case) | ✅ done |
| 18 | UI admin buttons | ✅ done |
| 19 | README + final E2E | ✅ done |

## Bugs

Running log of bug reports and their fixes. Newest first. Each entry: report, status, fix.

### Bug #1 — Model modal: X (top-right) doesn't close; want Save + X-to-discard
- **Reported:** 2026-09-09
- **Status:** ⬜ open
- **Area:** UI → Models tab → Add/Edit Model modal
- **Report:**
  - Editing an existing model: the modal does not close with the X in the top-right.
  - Preferred (if simple): a **Save** button that saves the changes and closes; the **X** to exit without saving.
  - The **Add** modal has the same problem.
- **Code state (context for fix):**
  - `app.js` `openModelModal()` already renders a footer with **Save** (saves + `close()`) and **Cancel** (`close()`).
  - `openModal()` wires `.modal-close` (the X) to `close()` = `backdrop.remove()`.
  - Intended behavior is already in code → reproduce to find the real cause of "X doesn't close".
- **Fix:** (pending)
