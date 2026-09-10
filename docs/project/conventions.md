# Convenzioni

## Naming
- *Resource.java → endpoint REST (JAX-RS)
- *Service.java → business logic
- *Repository.java → data access → EntityManager + @ApplicationScoped, @Transactional solo sulle scritture
- *Entity (domain/) → entity JPA; *Request/*Response (dto/) → payload REST
- *Exception (service/) → eccezioni API con status HTTP (base ApiException)
- META-INF/resources/*.html|js|css → frontend statico (SPA: index.html + styles.css + app.js)
- SPA app.js → state + api() fetch (non-2xx → toast+throw) + switchTab/openModal/toast; top bar: #btn-test-case (POST /api/admin/test-case→switchTab suites) + #btn-clean-db (POST /api/admin/clean→confirm→switchTab models)
- Suite editor → state.suiteDraft (working copy) + input live-bound; save = POST new / PUT existing (full replace testCases+paramSweeps)

## Config (.env + application.yml)
- .env (gitignored) → LLM_PROVIDER/LLM_BASE_URL/LLM_MODEL_NAME/LLM_API_KEY/LLM_TIMEOUT → Quarkus normalizza LLM_* → llm.*
- LlmConfig (config/) → @ConfigProperty llm.* con defaultValue (boot senza .env); apiKey → Optional<String> (empty→null, SRCFG00040); llm.timeout Duration PT15M → ModelFactory .timeout() su builder Ollama+OpenAI
- default model → ModelConfigService.ensureDefaultModel() (name "default", active) da LlmConfig; seed + admin test-case lo usano
- datasource → jdbc.url (nested under jdbc), db-kind, username, password; devservices.enabled=false (file H2, non in-memory)
- liquibase → change-log + migrate-at-start: true (obbligatorio); hibernate orm.ddl-auto: none (solo Liquibase)
- Liquibase YAML → colonne in block-style (- column:), mai flow-style inline {} (ParsedNodeException); backfill dati → changeSet `- sql: UPDATE ...` (es. 006 seed legacy → 0)

## Regola
- Endpoint REST → sempre in *Resource, mai in *Service
- UI chiama API → fetch() in app.js, non inline in HTML
- Parametri LLM → mai nel builder del modello, sempre in ChatRequestParameters a runtime via buildChatParams/applyCommon (provider-aware): temperature/topP/topK/frequencyPenalty/presencePenalty→same, maxTokens→.maxOutputTokens; null → omesso (default modello)
- Seed sweep → suite.seeds (CLOB JSON array); runSuite: resolveSeeds (vuoto→1 generato) × cases × combos; seed→RunResult.seed + ChatRequestParameters (OpenAI+Ollama); summary raggruppa per seed
- Token stats → saveResult: reasoningTokens solo OpenAiTokenUsage.outputTokensDetails() (altrimenti null); inputTps/outputTps = throughput(tokens,latencyMs) (null se latency/tokens sconosciuti)
- Judge prompt → placeholder {{task}}/{{expected}}/{{response}} (expected → "N/A" se null)
- JSON judge → parse leniente: strip ``` fence → isola {…} → Jackson (fallimento → score=null)
- Schema DB → solo Liquibase (mai Hibernate ddl-auto); mai modificare changelog esistenti, sempre nuovo file in db/changelog/changes/
- REST body → quarkus-rest-jackson (non quarkus-jackson standalone); @Consumes(APPLICATION_JSON) solo sui metodi che leggono body (create/update), mai a livello classe (POST senza body → 415)
- Suite validation → mode==NONE ⇒ judgeModelId required; mode!=NONE ⇒ expectedOutput required; paramName ∈ VALID_PARAMS (temperature,topP,topK,frequencyPenalty,presencePenalty,maxTokens); values = JSON array; judgeParams (temperature/topP/seed) + seeds opzionali
- Suite create/update → replaceChildren: delete testCases+paramSweeps esistenti poi insert (full replace, no JPA cascade)
- Routing → route suite-scoped in ONE *Resource (overlap cross-resource /api/suites vs /api+/suites/{id}/run → 404 HTML default)
- Run exceptions → SuiteAlreadyRunning 409, SuiteNotFound 404, NoActiveModel 400 (tutte ApiException)
- Run resiliente → runOne: call LLM + eval in try/catch; su failure (timeout ecc.) → salva RunResult evaluationType=ERROR (colonna VARCHAR(30), no migration; score=null, passed=false, scoreReason="<fase>: <Type>: <msg>") e ritorna → il loop prosegue (una combo lenta/errata non aborte la suite)
- Error handling → ApiExceptionMapper (ApiException → JSON status) + GenericExceptionMapper (last-resort: WebApplicationException keeps status, else JSON 500)
- Seed data → seed/SeedData.java @ApplicationScoped, @Transactional onStartup(@Observes StartupEvent); idempotente (solo se tabella vuota via findAll().isEmpty())
- DTO con id read-only → record *Dto con Long id come primo componente (popolato solo in toResponse, ignorato in create/update che usano accessors)
- Admin ops → AdminService: clean() wipe in FK order (run_result→test_case→param_sweep→test_suite→model_config); insertTestCase() ensureDefaultModel+activate + create/replace "code reviewer" (shared code-review user prompt × 2 system personas system-thinker/Audit-protocol × temp [0.1,1.0], judge=default)
- Test "no active model" → ModelConfigService.deactivateAll() prima (400 senza chiamare LLM), restore ensureDefaultModel() in finally (mai dipendere da LLM reale up/busy)
