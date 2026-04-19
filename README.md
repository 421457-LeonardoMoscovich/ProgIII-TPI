# Pokémon TCG — TPI Programación III

Implementación full-stack del Pokémon Trading Card Game (ruleset **XY1**, 146 cartas del set `xy1` de pokemontcg.io v2).

Spec autoritativa: [`TUP_3C_PIII_TPI_POKEMON_TCG.pdf`](./TUP_3C_PIII_TPI_POKEMON_TCG.pdf).
Plan completo: [`docs/00-plan-general.md`](./docs/00-plan-general.md) y breakdowns por sprint en [`docs/sprints/`](./docs/sprints/).

## Stack

| Capa | Tech |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | Angular 21 (strict TypeScript, SCSS) |
| DB | PostgreSQL 16 |
| Real-time | Spring WebSockets (STOMP + SockJS) |
| Tests | JUnit 5, Mockito, JaCoCo, Karma |
| Docs API | springdoc-openapi / Swagger UI |

## Requisitos

- Java 21 (Temurin recomendado)
- Node.js 20+ y npm 10+
- Docker Desktop (para Postgres)
- Maven 3.9+ (o usar `./mvnw`)

## Arranque rápido

```bash
# 1. Postgres
cp .env.example .env
./scripts/dev-up.sh      # docker compose up -d

# 2. Backend — http://localhost:8080
cd backend
./mvnw spring-boot:run   # aplica Flyway + bootstrapea caché xy1 en el primer arranque

# 3. Frontend — http://localhost:4200
cd frontend
npm install
npm start
```

Endpoints útiles:

| URL | Descripción |
|-----|-------------|
| `http://localhost:8080/actuator/health` | Healthcheck |
| `http://localhost:8080/swagger-ui.html` | API docs |
| `http://localhost:8080/api/cards?set=xy1` | Catálogo cacheado |
| `ws://localhost:8080/ws` | STOMP endpoint |

## Arquitectura (alto nivel)

```
┌────────────┐    HTTP/REST + STOMP/WS    ┌─────────────────────────┐
│  Angular   │ ─────────────────────────► │  Spring Boot            │
│  (browser) │ ◄───────────────────────── │  ├─ api (REST + WS)     │
└────────────┘                            │  ├─ application          │
                                          │  ├─ domain (engine) ★    │
                                          │  └─ infrastructure       │
                                          └──────────┬──────────────┘
                                                     │ JPA + Flyway
                                                     ▼
                                          ┌─────────────────────────┐
                                          │  PostgreSQL 16          │
                                          │  + xy1 card cache       │
                                          └─────────────────────────┘
    ★ Engine es transport-agnostic.
    ★ La API externa pokemontcg.io solo se consume en el bootstrap
       del catálogo — NUNCA durante una partida.
```

Invariantes críticos (ver [`CLAUDE.md`](./CLAUDE.md)):

1. El backend es la única fuente de verdad del estado de partida.
2. El Game Engine no conoce REST ni WebSockets.
3. El estado oculto del oponente nunca se serializa al rival.

## Comandos frecuentes

```bash
# Backend
./mvnw test                               # unit tests
./mvnw verify                             # tests + JaCoCo
./mvnw test -Dtest=RuleValidatorTest      # un test
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
npm test                                  # karma + jasmine
npm run lint
npm run build
```

## Estructura del repo

```
.
├── backend/              Spring Boot app
├── frontend/             Angular workspace
├── docs/                 Plan general + sprints + decisiones
├── scripts/              dev-up / dev-down
├── docker-compose.yml    Postgres local
└── .github/workflows/    CI (backend + frontend)
```

## Sprints

1. **Fundaciones** — scaffold + CI + xy1 cache + spike de WS ← _en curso_
2. Deck Builder
3. Game Engine (TDD-crítico)
4. Partida & persistencia
5. Tiempo real & UI
6. Calidad & entrega

## GitFlow

- `main` — releases.
- `develop` — integración.
- `feature/<nombre>` — feature en curso (PR a `develop`).
- `release/*`, `hotfix/*` según corresponda.
- PRs requieren ≥1 review y CI verde.
