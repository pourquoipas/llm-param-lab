# Convenzioni

## Naming
- *Resource.java → endpoint REST (JAX-RS)
- *Service.java → business logic
- *Repository.java → data access → EntityManager + @ApplicationScoped, @Transactional solo sulle scritture
- *Entity (domain/) → entity JPA
- *Request/*Response (dto/) → payload REST
- *Exception (service/) → eccezioni API con status HTTP (base ApiException)
- META-INF/resources/*.html|js|css → frontend statico (SPA: index.html + styles.css + app.js)
- SPA app.js → state object + api() fetch wrapper (non-2xx → toast+throw) + switchTab() + openModal() + toast()
- Suite editor → state.suiteDraft (working copy) + input live-bound; save = POST new / PUT existing (full replace testCases+paramSweeps)

## Config (application.yml)
- datasource → quarkus.datasource.jdbc.url (nested under jdbc), db-kind, username, password
- devservices → quarkus.datasource.devservices.enabled: false (file H2, non in-memory)
- liquibase → quarkus.liquibase.change-log + migrate-at-start: true (obbligatorio, altrimenti no-op)
- hibernate → quarkus.hibernate.orm.ddl-auto: none
- Liquibase YAML → colonne in block-style (- column:), mai flow-style inline {} (ParsedNodeException)

## Regola
- Endpoint REST → sempre in *Resource, mai in *Service
- UI chiama API → fetch() in app.js, non inline in HTML
- Parametri LLM (temperature/topP/maxTokens) → mai nel builder del modello, sempre in ChatRequestParameters a runtime
- paramName sweep → builder: temperature→.temperature, topP→.topP, maxTokens→.maxOutputTokens
- Judge prompt → placeholder {{task}}/{{expected}}/{{response}} (expected → "N/A" se null)
- JSON judge → parse leniente: strip ``` fence → isola {…} → Jackson (fallimento → score=null)
- Schema DB → solo Liquibase, mai Hibernate ddl-auto
- Changelog Liquibase → mai modificare file esistenti, sempre nuovo file in db/changelog/changes/
- @Consumes(APPLICATION_JSON) → solo sui metodi che leggono body (create/update), mai a livello classe (POST senza body → 415)
- REST JSON → quarkus-rest-jackson (non quarkus-jackson standalone) per reader/writer body REST
- Suite validation → mode==NONE ⇒ judgeModelId required; mode!=NONE ⇒ expectedOutput required; paramName ∈ {temperature,topP,maxTokens}; values = JSON array
- Suite create/update → replaceChildren: delete testCases+paramSweeps esistenti poi insert (full replace, no JPA cascade)
- Routing → route suite-scoped in ONE *Resource (overlap cross-resource /api/suites vs /api+/suites/{id}/run → 404 HTML default)
- Run exceptions → SuiteAlreadyRunning 409, SuiteNotFound 404, NoActiveModel 400 (tutte ApiException)
- Seed data → seed/SeedData.java @ApplicationScoped, @Transactional onStartup(@Observes StartupEvent); idempotente (solo se tabella vuota via findAll().isEmpty())
- DTO con id read-only → record *Dto con Long id come primo componente (popolato solo in toResponse, ignorato in create/update che usano accessors)
