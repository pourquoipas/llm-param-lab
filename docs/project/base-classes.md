# Classi base

## Config
- AppConfig → legge app.* da application.yml (default-judge-prompt)
- ModelFactory → costruisce ChatModel per ModelConfig (1.0.1: ChatModel, non ChatLanguageModel)

## Service
- ModelConfigService → CRUD + activate (isActive)
- TestSuiteService → CRUD suite + testCases + paramSweeps (cascade)
- JudgeService → CRUD judge + ensureDefaultJudge() (idempotente "all around", temp 0.0) + exists(id)
- TestRunnerService → runSuite / runAll (cartesian product + evaluation)
- SuiteAlreadyRunningException → run già in corso per la suite (→ HTTP 409 in REST)
- ApiException → base RuntimeException con status HTTP (404/409/400) + sottoclassi *Exception
- SuiteNotFoundException → suite non trovata (→ HTTP 404)
- ResultService → read: results(suiteId,testCaseId) + summary(suiteId) (raggruppa per seed → best combo); delete: deleteById/deleteByIds/deleteBySuite(suiteId,testCaseId)/deleteAll
- NoActiveModelException → nessun modello attivo (→ HTTP 400)
- JudgeNotFoundException → judge non trovata (→ HTTP 404)
- JudgeNameAlreadyExistsException → nome judge già usato (→ HTTP 409)

## REST
- ApiExceptionMapper → @Provider ExceptionMapper<ApiException> → JSON {"error": msg}
- JudgeResource → /api/judges CRUD (GET list, GET/{id}, POST 201, PUT/{id}, DELETE/{id} 204)
- ResultResource → /api/run-all, /api/results (+delete), /api/results/summary, /api/results/export/{xlsx,pdf} (run suite in TestSuiteResource)

## DTO
- JudgeResponse → record { score, reason } da JSON del judge (@JsonIgnoreProperties ignoreUnknown)
- ModelConfigRequest/Response → record payload REST (Response.from(entity))
- SuiteCreateRequest → flat fields: suite + judgeId + List<TestCaseDto> + List<ParamSweepDto> + List<Integer> seeds
- SuiteResponse → record suite (judgeId) + testCases + paramSweeps + latestRunAt
- JudgeConfigRequest → record { name, modelId, prompt, temperature, topP, seed } (modelId/prompt/temperature null → default a runtime)
- JudgeConfigResponse → record + createdAt/updatedAt (JudgeConfigResponse.from(Judge))
- TestCaseDto → record { name, systemPrompt, userPrompt, sortOrder }
- ParamSweepDto → record { paramName, values (JSON array string) }
- RunSummaryResponse → suiteId + List<SeedSummary> (seed → List<TestCaseSummary>: bestCombo + combos: paramsJson, avgScore, min/maxLatency)

## Domain
- ModelConfig → provider, baseUrl, modelName, isActive
- TestSuite → expectedOutput, judgeId (→ Judge), seeds (CLOB JSON array)
- Judge → name (unique), modelId (null→attivo), prompt, temperature, topP, seed
- TestCase → systemPrompt (per-case), userPrompt, sortOrder
- ParamSweep → paramName, values (JSON array)
- RunResult → paramsJson, rawOutput, score, evaluationType, passed, seed, reasoningTokens, inputTps/outputTps

## Regola
- ModelFactory → mai parametri nel builder, sempre a call-time
- TestRunnerService → max 1 run concorrente per suite (ConcurrentHashMap lock)
- Judge → temperature 0, passed = score >= 0.5 (JUDGE_PASS_THRESHOLD)
