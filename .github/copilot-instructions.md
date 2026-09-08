## DOC (NON NEGOZIABILE)

### START
- Se `docs/` non esiste: creala. Crea `docs/INDEX.md` con:
  ```
  # [NOME PROGETTO]

  ## Convenzioni e classi base
  → project/conventions.md
  → project/base-classes.md
  ```
- Se `docs/` esiste: leggi `docs/INDEX.md`. Se il task corrisponde a una riga, leggi quel file.

### FORMATO (obbligatorio)
- Max 40 righe per file
- Solo: liste con →, titoli §, una riga per entry
- Vietato: tabelle markdown
- Vietato: frasi che iniziano con "In questo progetto", "Perché", "È importante", "Si consiglia"
- Vietato: più di 1 riga per entry
- Se non si scrive in 1 riga, non è informazione, è prosa. Non scriverlo.

### CREA un file nuovo quando:
- Hai usato un pattern/classe/convenzione NON presente in docs/
- Path: docs/project/ (o docs/project/domain/, docs/project/utilities/)
- Nome file: kebab-case, max 3 parole
- Aggiungi la riga in INDEX.md

### AGGIORNA un file esistente quando:
- Hai trovato una regola non documentata
- Hai trovato un'eccezione
- La doc è sbagliata rispetto al codice (il codice vince)
- NON riscrivere il file. Aggiungi o modifica solo la riga che serve.

### INDEX.md
- Max 50 righe
- Formato: ### ...[trigger] → [path]
- Una riga per entry

### ESEMPIO (questo è il formato corretto, non un suggerimento. I nomi sono fittizi.)

File: docs/project/conventions.md

```
# Convenzioni

## Naming
- *Resource.java → endpoint REST → estende BaseResource
- *Service.java → business logic → estende BaseService
- *Entity.java → entity JPA → estende BaseEntity
- *Repo.java → data access (repository)
- pages/*.html → pagina UI (CSS e JS inline o in assets/)
- assets/*.js → logica frontend condivisa

## Regola
- Endpoint REST → sempre in *Resource, mai in *Service
- UI chiama API → fetch() in assets/*.js, non inline in HTML
- Entity con relazioni → estende BaseHeadEntity se è il record principale
```

File: docs/project/base-classes.md

```
# Classi base

## REST
- BaseResource → base tutti gli endpoint *Resource

## Service
- BaseService → base tutte le *Service

## Entity
- BaseEntity → base tutte le *Entity
- BaseHeadEntity → base entity principali (record head)

## Regola
- Se una classe estende una base, NON ri-creare la logica che la base già fornisce
```

File: docs/INDEX.md (righe aggiunte)

```
### ...naming classi/endpoint → project/conventions.md
### ...classi base e gerarchie → project/base-classes.md
```


# quarkus project
- project with java and quarkus, use quarkus cli command to create and modify packages if need.
- check java version installed to get correct libraries and addons.
- always use the best practices for writing software.
- always use clean programming paradigm
- rely on last material guidelines but think for very minimal and futuristic ui, minimize the user work and coiches by driving it's work.
- always write tests and run tests to check programm after every modification.
- always document project, classes and behaviours with .md files.
- create and update a readme.md with an overview.
- use a free license: Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International. For the full license text, please visit: http://creativecommons.org/licenses/by-nc-sa/4.0/

