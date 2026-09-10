# llm-param-lab

A tool to test a local (or remote) LLM across multiple prompts and parameter
combinations, then evaluate each result either against an expected output or via
a second "judge" LLM. Built with Java 21, Quarkus 3 and Langchain4j 1.x.

## Prerequisites

- Java 21
- Maven 3.9+
- Ollama running locally (optional — only needed for `OLLAMA` models)

## Quick start

1. `./mvnw quarkus:dev`
2. Open http://localhost:8080 in a browser
3. The default model (from `.env`) is already active — or add your own (Models tab)
4. Click **＋ Test case** (top bar) to insert a ready-to-run suite, or create one (Suites tab)
5. Click "Run Suite"
6. Check results (Results tab)

On first start the app seeds ONE default model (from `.env`, flagged active) and
one example suite (`JSON extraction test`). Seeding is idempotent — it only runs
when the tables are empty, so restarts never duplicate data.

The top bar has two admin buttons:
- **＋ Test case** — inserts the `code reviewer` live suite: one shared code-review user prompt
  (a non-thread-safe `SimpleCache` under 10k req/s) × 2 system personas (`system thinker`,
  `Audit protocol`) × temperature `[0.1, 1.0]`, judged by the default model.
- **Clean DB** — wipes the entire database (all models, suites, and results).

## Interface (UI)

Single page (`index.html` + `app.js`, no build step) with four tabs:
- **Models** — CRUD for `ModelConfig`; one model is flagged *active* (the one that runs and
  judges by default). "Activate" sets the active model.
- **Suites** — list + create/edit. Editor fields: name/description, expected-output mode and
  value, judge model/prompt/temperature/topP/seed, a **Seed Sweep** field (comma-separated
  ints), a test-case list (name/system/user prompts), and a param-sweep list
  (param name + JSON-array values). **Save** persists and closes; **X/Cancel** discard.
- **Results** — pick a suite (and optionally one test case). Top: "Best param combo (per seed)"
  summary card (one block per seed, best combo + avg score per test case). Below: a results
  table — Test Case, Params, Seed, Score, Passed, Latency, Tokens In/Out, Thinking,
  In/Out t/s, Eval Type, Date. Click a row to expand raw output + score reason.
  "Run Suite" / "Run All" trigger runs.
- **top bar** — **＋ Test case** (insert the ready-to-run `code reviewer` suite) and
  **Clean DB** (wipe everything).

## Patterns
- **New chat per task** — every LLM call (model under test *and* judge) is a stateless
  single-turn `chat()` from a fresh message list; no `ChatMemory`, so no cross-task memory.
- **Provider-aware parameters** — one `buildChatParams`/`applyCommon` builds
  `ChatRequestParameters` per provider (OpenAI-compatible vs Ollama); nulls are omitted so the
  model's own defaults apply; `seed` is set on both providers; `reasoningEffort` is
  OpenAI-compatible-only.
- **Resilient run** — each combo is isolated: a failure (e.g. timeout) is recorded as an
  `ERROR` result and the run continues with the remaining combos.
- **Seed sweep** — runs are the Cartesian product of seeds × test cases × param combos; the
  summary groups and compares at parity of seed.
- **Idempotent seeding** — on first start, the default model + example suite are created only
  when the tables are empty (never duplicated on restart).
- **Schema versioning** — all DB changes are additive Liquibase changeSets (one new file per
  change, never edit an existing one); data is preserved.
- **Offline test suite** — tests fake the `ChatModel` (a subclass) and inject an in-memory
  repository stub; nothing talks to a real LLM or network.

## Configuration (.env)

The default LLM is configured in a gitignored `.env` file in the project root
(copy `.env.example` to start). Quarkus maps the keys to `llm.*`:

| Key | Default | Meaning |
|-----|---------|---------|
| `LLM_PROVIDER` | `OPENAI_COMPATIBLE` | `OPENAI_COMPATIBLE` or `OLLAMA` |
| `LLM_BASE_URL` | `http://localhost:11000/v1` | Base URL of the LLM endpoint |
| `LLM_MODEL_NAME` | `Qwen3.8-27B-UD-IQ3_S.gguf` | Model name to request |
| `LLM_API_KEY` | *(empty)* | API key (optional) |
| `LLM_TIMEOUT` | `PT15M` | Per-request LLM timeout (ISO-8601); raise for slow local models |

On first start the app creates a single **default** model from these values and
flags it active. The same model is used for execution (the active model) and for
evaluation (the judge), so one `.env` drives both.

**Run resilience:** every combo's LLM call uses the timeout above. If a combo fails
(e.g. it times out), it is recorded as an `ERROR` result (no score, `scoreReason`
explains why) and the run **continues** with the remaining combos — a single slow or
failing combo never aborts the whole suite.

**New chat per task:** every LLM call (the model under test *and* the judge) is a
stateless, single-turn `chat()` call built from a fresh message list — no `ChatMemory`
is wired. A test case (or a judge evaluation) never sees messages from a previous task,
so results are not contaminated by conversation memory.

**Judge parameters (savable per suite):** judge `temperature` (defaults to **0.0** when
empty — deterministic), judge `topP`, and judge `seed`. Stored on the suite so different
domains (logic, data analysis, creative writing) can keep distinct judge configurations.
Applied provider-aware: `seed` is set on both OpenAI-compatible and Ollama; nulls are
omitted so the model's own default applies.

**Sweepable parameters:** a suite can sweep any of these chat-level parameters (applied
per call via `ChatRequestParameters`, *not* pre-set on the model): `temperature`, `topP`,
`topK`, `frequencyPenalty`, `presencePenalty`, `maxTokens`. Provider-agnostic (works for
both OpenAI-compatible and Ollama). `seed` is not swept per combo — it is the separate
seed sweep (see above). `reasoningEffort` is OpenAI-compatible-only and not exposed in the
UI. If a param is not swept, the model's own default is used (no token cap is injected
unless `maxTokens` is swept).

**Raw-JSON injection (NOT supported):** langchain4j 1.0.1 serializes
`ChatRequestParameters` to a fixed schema — there is no hook to inject arbitrary JSON
(e.g. `"reasoning": {"effort": "low"}`) into the chat request. The typed
`reasoningEffort(String)` field (OpenAI-compatible) *is* available and covers the
`reasoning.effort` case. Any other non-typed request field is not reachable through
langchain4j.

**Seed sweep (per-seed runs + comparison):** a suite carries an optional comma-separated
list of integer seeds. All cases × combos are re-run once per seed (number of seeds = number
of full runs). An empty list → one seed is generated and applied to every run. Every
`RunResult` stores its `seed`, and the results summary **groups and compares per seed**.
The seed is passed to the model (Ollama `seed` / OpenAI-compatible `seed`). UI: "Seed Sweep"
field in the suite editor; one summary block per seed.

**Token / throughput stats (per run):** every `RunResult` records `tokensIn` / `tokensOut`
(from `TokenUsage`), plus two derived stats:
- `reasoningTokens` — "thinking" tokens. Only reported by the OpenAI-compatible backend
  (`OpenAiTokenUsage.outputTokensDetails().reasoningTokens()`); `null` for Ollama.
- `inputTps` / `outputTps` — tokens/second = token count ÷ total call latency (ms). `null`
  when the token count or latency is unknown (e.g. an errored run).

**Limitation (documented):** langchain4j 1.0.1 does **not** expose the *time* spent thinking
vs. generating — only token counts and the total call latency. So "thinking time" is not
measured; `reasoningTokens` is the closest available signal (and only on OpenAI-compatible).
UI: the results table shows Thinking tokens and In/Out t/s columns.

## Schema versioning

All database changes go through Liquibase changelogs in `db/changelog/changes/`.
To add a column:

1. Create a new yaml file (e.g. `002-add-foo.yaml`)
2. Add an `include` line to `db/changelog/db.changelog-master.yaml`
3. Restart — Liquibase applies the change, existing data is preserved

Never edit an existing changelog file; always add a new one.

## API reference

Base path: `http://localhost:8080`

### Models

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/models` | List models |
| POST | `/api/models` | Create a model (201) |
| PUT | `/api/models/{id}` | Update a model |
| DELETE | `/api/models/{id}` | Delete a model (204) |
| POST | `/api/models/{id}/activate` | Set as the active model |

### Suites

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/suites` | List suites |
| POST | `/api/suites` | Create a suite (201) |
| GET | `/api/suites/{id}` | Get a suite (with test cases + sweeps) |
| PUT | `/api/suites/{id}` | Update a suite (full replace of test cases + sweeps) |
| DELETE | `/api/suites/{id}` | Delete a suite (204) |
| POST | `/api/suites/{id}/run` | Run the suite, returns results |

### Run + Results

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/run-all` | Run every suite, returns all results |
| GET | `/api/results?suiteId={id}&testCaseId={id}` | Filtered results |
| GET | `/api/results/summary?suiteId={id}` | Per-suite summary (grouped by seed) |

**Results** (`GET /api/results`) returns `RunResult` objects: `id`, `suiteId`, `testCaseId`,
`seed`, `modelConfigId`, `paramsJson`, `rawOutput`, `latencyMs`, `tokensIn`, `tokensOut`,
`reasoningTokens`, `inputTps`, `outputTps`, `score`, `scoreReason`, `evaluationType`,
`passed` (`null` when `SKIPPED`), `createdAt`.

**Summary** (`GET /api/results/summary`) shape — `RunSummaryResponse`:
- `suiteId`, `seeds[]` → `SeedSummary{ seed, testCases[] }` (one per seed, nulls last)
- `testCases[]` → `TestCaseSummary{ testCaseId, testCaseName, bestCombo, combos[] }`
- `combos[]` → `ComboSummary{ paramsJson, avgScore, minLatencyMs, maxLatencyMs, runCount }`

### Admin

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/clean` | Wipe the entire database (204) |
| POST | `/api/admin/test-case` | Insert the `code reviewer` suite (201) |

### Example: create a model

```bash
curl -X POST http://localhost:8080/api/models \
  -H "Content-Type: application/json" \
  -d '{
    "name": "local-llama",
    "provider": "OLLAMA",
    "baseUrl": "http://localhost:11434",
    "modelName": "llama3"
  }'
```

### Example: create a suite

```bash
curl -X POST http://localhost:8080/api/suites \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Code review sweep",
    "description": "Same user prompt, different system prompts and parameters.",
    "expectedOutput": null,
    "expectedOutputMode": "JUDGE",
    "judgeModelId": null,
    "judgePrompt": null,
    "judgeTemperature": 0.0,
    "judgeTopP": null,
    "judgeSeed": null,
    "seeds": [42, 7],
    "testCases": [
      {
        "name": "audit",
        "systemPrompt": "You are a meticulous code auditor.",
        "userPrompt": "Review this Java code ...",
        "sortOrder": 0
      }
    ],
    "paramSweeps": [
      { "paramName": "temperature", "values": "[0.1, 1.0]" }
    ]
  }'
```

Field reference (create = `SuiteCreateRequest`, read = `SuiteResponse`):
- `name`, `description`
- `expectedOutput` (string) + `expectedOutputMode` — `NONE` | `EXACT` | `CONTAINS` | `JUDGE`.
  `JUDGE` → evaluated by the judge LLM (uses `judgePrompt` + judge params); `NONE` → `SKIPPED`
  (no score); `EXACT`/`CONTAINS` → compared against `expectedOutput`.
- `judgeModelId` — null → use the active model as judge; `judgePrompt`; `judgeTemperature`
  (null → 0.0), `judgeTopP`, `judgeSeed` (null → judge uses model default)
- `seeds` — optional list of integer seeds (seed sweep); empty/omitted → one generated seed
- `testCases[]` — `name`, `systemPrompt`, `userPrompt`, `sortOrder` (`id` is read-only)
- `paramSweeps[]` — `paramName` + `values` (JSON array string). Sweepable `paramName`:
  `temperature`, `topP`, `topK`, `frequencyPenalty`, `presencePenalty`, `maxTokens`
- `seed` is not swept per test case; it is the suite-level `seeds` list. Read response also
  returns `id`, `createdAt`, `updatedAt`, `latestRunAt`.

## License

Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).
For the full license text, see <http://creativecommons.org/licenses/by-nc-sa/4.0/>.
