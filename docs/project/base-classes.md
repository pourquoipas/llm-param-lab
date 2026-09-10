# Classi base

## Config
- AppConfig → legge app.* da application.yml (default-judge-prompt)
- ModelFactory → costruisce ChatModel per ModelConfig (1.0.1: ChatModel, non ChatLanguageModel)

## Service
- ModelConfigService → CRUD + activate (isActive)
- TestSuiteService → CRUD suite + testCases + paramSweeps (cascade)
- TestRunnerService → runSuite / runAll (cartesian product + evaluation)
- SuiteAlreadyRunningException → run già in corso per la suite (→ HTTP 409 in REST)
- ApiException → base RuntimeException con status HTTP (404/409/400) + sottoclassi *Exception
- SuiteNotFoundException → suite non trovata (→ HTTP 404)
- ResultService → results(suiteId,testCaseId) + summary(suiteId) (raggruppa per seed → best combo per test case)
- NoActiveModelException → nessun modello attivo (→ HTTP 400)

## REST
- ApiExceptionMapper → @Provider ExceptionMapper<ApiException> → JSON {"error": msg}
- ResultResource → /api/run-all, /api/results, /api/results/summary (run suite in TestSuiteResource)

## DTO
- JudgeResponse → record { score, reason } da JSON del judge (@JsonIgnoreProperties ignoreUnknown)
- ModelConfigRequest/Response → record payload REST (Response.from(entity))
- SuiteCreateRequest → flat fields: suite + judge (modelId/prompt/temperature/topP/seed) + List<TestCaseDto> + List<ParamSweepDto> + List<Integer> seeds
- SuiteResponse → record suite + testCases + paramSweeps + latestRunAt
- TestCaseDto → record { name, systemPrompt, userPrompt, sortOrder }
- ParamSweepDto → record { paramName, values (JSON array string) }
- RunSummaryResponse → suiteId + List<SeedSummary> (seed → List<TestCaseSummary>: bestCombo + combos: paramsJson, avgScore, min/maxLatency)

## Domain
- ModelConfig → provider, baseUrl, modelName, isActive
- TestSuite → expectedOutput, judge (modelId/prompt/temperature/topP/seed), seeds (CLOB JSON array)
- TestCase → systemPrompt (per-case), userPrompt, sortOrder
- ParamSweep → paramName, values (JSON array)
- RunResult → paramsJson, rawOutput, score, evaluationType, passed, seed, reasoningTokens, inputTps/outputTps

## Regola
- ModelFactory → mai parametri nel builder, sempre a call-time
- TestRunnerService → max 1 run concorrente per suite (ConcurrentHashMap lock)
- Judge → temperature 0, passed = score >= 0.5 (JUDGE_PASS_THRESHOLD)
