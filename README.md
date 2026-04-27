# Pokemon TCG - TPI Programacion III

Implementacion full-stack del Pokemon Trading Card Game para el ruleset XY1. El backend mantiene el estado autoritativo de la partida y el frontend consume snapshots REST y eventos en tiempo real via STOMP/WebSocket.

Spec autoritativa: [`TUP_3C_PIII_TPI_POKEMON_TCG.pdf`](./TUP_3C_PIII_TPI_POKEMON_TCG.pdf)  
Plan del proyecto: [`docs/00-plan-general.md`](./docs/00-plan-general.md)  
Breakdown por sprint: [`docs/sprints/`](./docs/sprints/)

## Entrega tecnica

- Arquitectura: [`docs/arquitectura.md`](./docs/arquitectura.md)
- Despliegue: [`docs/manual-despliegue.md`](./docs/manual-despliegue.md)
- Performance: [`docs/performance.md`](./docs/performance.md)
- QA Sprint 5: [`docs/sprints/sprint-5-qa-acceptance.md`](./docs/sprints/sprint-5-qa-acceptance.md)
- SQL: [`scripts/db/schema.sql`](./scripts/db/schema.sql) y [`scripts/db/seed.sql`](./scripts/db/seed.sql)

## Stack

| Capa | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Maven Wrapper |
| Frontend | Angular 21, TypeScript strict, SCSS |
| Base de datos | PostgreSQL 16 |
| Tiempo real | Spring WebSocket, STOMP, SockJS |
| Testing backend | JUnit 5, Mockito, JaCoCo |
| Testing frontend | Vitest via `ng test`, ESLint |
| API docs | springdoc-openapi, Swagger UI |

## Requisitos

- Java 21
- Node.js 20+ y npm
- Docker Desktop / Docker Compose v2

## Variables de entorno

Base local: [`.env.example`](./.env.example)

Variables principales:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `POKEMONTCG_API_KEY` (opcional)

Para despliegue productivo ver [`docs/manual-despliegue.md`](./docs/manual-despliegue.md).

## Arranque local

### 1. Base de datos

```bash
cp .env.example .env
./scripts/dev-up.sh
```

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend disponible en `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Frontend disponible en `http://localhost:4200`.

## Endpoints utiles

| URL | Descripcion |
| --- | --- |
| `http://localhost:8080/actuator/health` | Healthcheck |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/api/cards?set=xy1` | Catalogo cacheado |
| `http://localhost:8080/ws` | Endpoint SockJS/STOMP |

## Arquitectura

```text
Angular UI
  | REST: auth, catalogo, deck builder, lobby, snapshots
  | STOMP/SockJS: acciones y eventos de partida
Spring Boot API
  | api.controller
  | api.ws
application.service
  | MatchSessionService
  | DeckService
  | CardCatalogService
domain
  | engine puro y agnostico al transporte
  | reglas, acciones, eventos, validaciones
infrastructure
  | JPA repositories
  | cliente pokemontcg.io
PostgreSQL + Flyway
```

Invariantes importantes:

1. El backend es la unica fuente de verdad del estado de partida.
2. `domain.engine` no depende de REST, WebSocket ni JPA.
3. La mano del rival nunca se serializa ni por REST ni por WS.

## Comandos utiles

```bash
# Backend
cd backend
./mvnw test
./mvnw verify
./mvnw test -Dtest=RuleValidatorTest

# Frontend
cd frontend
npm test
npm run lint
npm run build

# Deploy local tipo prod
docker compose --env-file .env.prod -f config/docker-compose.prod.yml up --build -d
```

## Cobertura y calidad

- `./mvnw verify` ejecuta tests y gate de JaCoCo.
- Cobertura global minima: 80%.
- Cobertura minima para `RuleValidator`, `DamageCalculator` y `StatusEffectManager`: 90%.
- Sprint 5 agrega cobertura de reconnect, hand masking y flujo realtime base.

## Estructura del repo

```text
.
|-- backend/
|-- frontend/
|-- docs/
|-- scripts/
|-- config/
|-- docker-compose.yml
`-- AGENTS.md
```

## Estado por sprint

1. Sprint 1 - Fundaciones
2. Sprint 2 - Deck Builder
3. Sprint 3 - Game Engine
4. Sprint 4 - Partida y persistencia
5. Sprint 5 - Tiempo real y UI del tablero
6. Sprint 6 - Calidad y entrega
