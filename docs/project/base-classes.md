# Classi base

## Config
- AppConfig → legge app.* da application.yml (default-judge-prompt)
- ModelFactory → costruisce ChatModel per ModelConfig (1.0.1: ChatModel, non ChatLanguageModel)

## Service
- ModelConfigService → CRUD + activate (isActive)
- TestSuiteService → CRUD suite + testCases + paramSweeps (cascade)
- TestRunnerService → runSuite / runAll (cartesian product + evaluation)
- SuiteAlreadyRunningException → run già in corso per la suite (→ HTTP 409 in REST)

## Domain
- ModelConfig → provider, baseUrl, modelName, isActive
- TestSuite → expectedOutput, judgeModelId, judgePrompt
- TestCase → systemPrompt (per-case), userPrompt, sortOrder
- ParamSweep → paramName, values (JSON array)
- RunResult → paramsJson, rawOutput, score, evaluationType, passed

## Regola
- ModelFactory → mai parametri nel builder, sempre a call-time
- TestRunnerService → max 1 run concorrente per suite (ConcurrentHashMap lock)
