# Sprint 1 — Fundaciones

**Estado:** ✅ COMPLETADO  
**Rama:** `develop`  
**Commits:** `4f7c572` → `00ee5d2`

## Objetivo

Infraestructura lista para desarrollo paralelo del equipo: esqueleto BE/FE, DB corriendo, CI verde, caché de cartas xy1 cargado.

---

## Tareas

### INFRA-01 — Repositorio GitHub con GitFlow ✅
- `main` y `develop` protegidos
- PR templates y issue templates en `.github/`
- `.gitignore` configurado para Java + Node + IDE

### INFRA-02 — Docker Compose + PostgreSQL 16 ✅
- `docker-compose.yml` con servicio `postgres:16-alpine`
- Volumen persistente, `.env.example`
- Scripts `dev-up.sh` / `dev-down.sh`

### INFRA-03 — CI GitHub Actions ✅
- Workflow backend: `mvn verify` con caché Maven
- Workflow frontend: `npm ci` → `lint` → `build` → `test` (Vitest, sin flag `--browsers`)
- **Fix:** step de lint agregado retroactivamente (faltaba en versión inicial)

### BE-01 — Spring Boot Skeleton ✅
- Java 21 + Spring Boot 3.x + Maven
- Dependencias: `web`, `data-jpa`, `validation`, `websocket`, `actuator`, `postgresql`, `flyway`, `springdoc-openapi`
- Estructura de paquetes: `com.utn.pokemontcg.{api,domain,infrastructure,application,config}`

### BE-02 — Flyway Schema V1 ✅
- Migración `V1__initial_schema.sql`
- Tablas: `users`, `cards`, `decks`, `deck_cards`, `matches`, `match_events`, `match_states` (JSONB para snapshot de estado)
- Índices en: `cards.set_code`, `cards.name`, `decks.user_id`, `matches.status`, `match_events(match_id, created_at)`

### BE-03 — CardCatalogService / Caché xy1 ✅
- Descarga las 146 cartas del set `xy1` de `pokemontcg.io` en startup (`ApplicationReadyEvent`) si la tabla está vacía
- `GET /api/cards?set=xy1` sirve desde DB (nunca toca la API externa en runtime)
- **Fix:** orden de cartas era lexicográfico — corregido a numérico
- **Fix:** campo `rules` faltaba en `CardDto` y modelo frontend — agregado

### BE-04 — Swagger / OpenAPI ✅
- `springdoc-openapi` configurado
- Accesible en `/swagger-ui.html`
- `OpenApiConfig` implementado

### FE-01 — Angular Skeleton ✅
- Angular 21, strict TypeScript, SCSS, SSR deshabilitado
- Estructura: `core/`, `features/{deck-builder,lobby,match}/`, `shared/`, `layouts/`
- **Fix:** componente raíz renombrado de `App` a `AppComponent` (ESLint + convención Angular)

### FE-02 — HTTP Services + Modelos ✅
- `ApiService` con interceptor de errores
- Modelos tipados: `Card`, `Deck`, `Match`
- `CardService` consumiendo `/api/cards`
- **Fix:** timeout HTTP de `PokemonTcgApiClient` ahora se aplica correctamente al `RestClient`

### SPIKE-01 — WebSocket POC ✅
- Endpoint STOMP echo en backend
- Cliente Angular con `@stomp/ng2-stompjs`
- Decisiones documentadas en `docs/decisiones/ws-transport.md`
- *(Spike vivía en rama `spike/websockets`, mergeado en develop)*

### DOC-01 — README ✅
- Instrucciones de setup: Java 21 + Node 20+ + Docker
- Diagrama de arquitectura
- Link a `docs/00-plan-general.md`

---

## Testing

| Scope | Estado |
|-------|--------|
| Backend unit tests (JUnit 5) | ✅ 2 tests, 1 skipped — green |
| Frontend unit tests (Vitest) | ✅ green |
| Testcontainers (integración DB) | ⏭️ **Diferido a Sprint 3** — problema de conexión al socket Docker en Windows; dependencias removidas de `pom.xml` |

---

## Definition of Done

| Criterio | Estado |
|----------|--------|
| Env local levanta en < 15 min | ✅ |
| CI verde en `develop` | ✅ |
| 146 cartas xy1 en DB | ✅ |
| WebSocket POC validado | ✅ |
| Swagger accesible | ✅ |

---

## Deuda técnica / Notas para sprints futuros

- **Testcontainers** diferido a Sprint 3 — requiere configurar Docker socket en Windows o correr tests en Linux (CI ya funciona)
- Frontend smoke page (`/`) muestra grilla de cartas xy1 — componente temporal, se reemplaza en Sprint 2 con Deck Builder real
- ESLint agregado post-scaffolding — configuración en `eslint.config.js` (flat config)
