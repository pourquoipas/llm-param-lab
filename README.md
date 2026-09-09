# llm-param-lab

A tool to test a local (or remote) LLM across multiple prompts and parameter
combinations, then evaluate each result either against an expected output or via
a second "judge" LLM. Built with Java 21, Quarkus 3 and Langchain4j 1.x.

## Prerequisites

- Java 21
- Maven 3.9+
- Ollama running locally (optional — only needed for `OLLAMA` models)

## Quick start

1. `mvn quarkus:dev`
2. Open http://localhost:8080 in a browser
3. Add a model (Models tab)
4. Create a suite (Suites tab)
5. Click "Run Suite"
6. Check results (Results tab)

On first start the app seeds two models (`local-llama`, `remote-gpt`) and one
example suite (`JSON extraction test`). Seeding is idempotent — it only runs when
the tables are empty, so restarts never duplicate data.

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
| GET | `/api/results/summary?suiteId={id}` | Per-suite summary (best param combo per test case) |

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
    "name": "JSON extraction test",
    "description": "Same user prompt, different system prompts and parameters.",
    "expectedOutput": null,
    "expectedOutputMode": "NONE",
    "judgeModelId": 2,
    "judgePrompt": null,
    "testCases": [
      {
        "name": "extract-name-simple",
        "systemPrompt": "You are a data extraction assistant. Return JSON only.",
        "userPrompt": "Extract the name from: My name is John.",
        "sortOrder": 0
      }
    ],
    "paramSweeps": [
      { "paramName": "temperature", "values": "[0.0, 0.3, 0.7]" }
    ]
  }'
```

`paramSweeps[].values` is a JSON array string. `paramName` is one of
`temperature`, `topP`, `maxTokens`.

## License

Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).
For the full license text, see <http://creativecommons.org/licenses/by-nc-sa/4.0/>.
