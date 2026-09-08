# Convenzioni

## Naming
- *Resource.java → endpoint REST (JAX-RS)
- *Service.java → business logic
- *Repository.java → data access (Hibernate)
- *Entity (domain/) → entity JPA
- *Request/*Response (dto/) → payload REST
- META-INF/resources/*.html|js|css → frontend statico

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
- Schema DB → solo Liquibase, mai Hibernate ddl-auto
- Changelog Liquibase → mai modificare file esistenti, sempre nuovo file in db/changelog/changes/
