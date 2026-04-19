# Sprint 1 — Fundaciones

**Objetivo:** dejar la infraestructura lista para que todos los equipos puedan empezar a desarrollar en paralelo sin bloqueos. Al final del sprint hay skeleton backend, skeleton frontend, base de datos corriendo, CI funcionando y caché de cartas del set `xy1` cargada.

**Duración estimada:** 1–2 semanas
**RF/RNF cubiertos:** parcial de RNF-02, RNF-03 (setup), base para RF-03, RF-04

## Tareas

### INFRA-01 — Repositorio y GitFlow
- Crear repo en GitHub Classroom
- Configurar ramas `main`, `develop`, plantillas de `feature/*`, `release/*`, `hotfix/*`
- Proteger `main` y `develop` (PR obligatorio, ≥1 review)
- Crear `.gitignore`, `LICENSE`, plantilla de PR e issue
- **Done:** todos los miembros cloneados y con acceso

### INFRA-02 — Docker Compose para entorno local
- `docker-compose.yml` con PostgreSQL 16
- Volumen persistente, variables de entorno en `.env.example`
- Script `scripts/dev-up.sh` / `dev-down.sh`
- **Done:** `docker compose up` levanta Postgres accesible en localhost

### INFRA-03 — CI en GitHub Actions
- Workflow backend: `mvn verify` en push a `develop` y PRs
- Workflow frontend: `npm ci && npm run lint && npm run build && npm test`
- Cache de Maven y npm
- **Done:** badge de CI en README, corre verde

### BE-01 — Skeleton Spring Boot
- Proyecto Maven con Spring Boot 3.x, Java 21
- Dependencias base: `web`, `data-jpa`, `validation`, `websocket`, `actuator`, `postgresql`, `flyway`, `springdoc-openapi`
- Estructura de paquetes:
  ```
  com.utn.pokemontcg
    ├── api            (controllers, dto, ws)
    ├── domain         (entidades de dominio, engine)
    ├── infrastructure (repos, clientes externos)
    ├── application    (services, casos de uso)
    └── config
  ```
- Configuración de perfiles `dev` / `test` / `prod`
- **Done:** `mvn spring-boot:run` arranca en `:8080/actuator/health`

### BE-02 — Flyway + esquema inicial
- Migración `V1__initial_schema.sql` con tablas base: `users`, `cards`, `decks`, `deck_cards`, `matches`, `match_events`, `match_states`
- `match_states` usa `JSONB` para el snapshot
- Índices: `cards.set_code`, `cards.name`, `decks.user_id`, `matches.status`, `match_events.match_id + created_at`
- **Done:** migración corre al arrancar, esquema creado

### BE-03 — Cliente pokemontcg.io con caché
- Cliente HTTP (RestClient o WebClient) con retry y timeout
- Servicio `CardCatalogService` que:
  - Al arranque verifica si hay cartas del set `xy1` en BD; si no, las descarga y persiste
  - Expone consultas al caché local (no golpea API externa)
- Mapper de DTO externo → entidad `Card` (HP, ataques, debilidad, resistencia, costo de retirada, supertype, subtypes)
- **Done:** `GET /api/cards?set=xy1` devuelve 146 cartas desde BD

### BE-04 — Swagger/OpenAPI
- Configurar `springdoc-openapi`
- UI en `/swagger-ui.html`
- Anotar endpoints existentes
- **Done:** UI accesible con el endpoint de cartas documentado

### FE-01 — Skeleton Angular
- `ng new` con routing, SCSS, SSR off, strict TS
- Estructura:
  ```
  src/app/
    ├── core       (servicios singleton, interceptors, models)
    ├── features/
    │   ├── deck-builder
    │   ├── lobby
    │   └── match
    ├── shared     (componentes reutilizables)
    └── layouts
  ```
- ESLint + Prettier configurados
- Variables de entorno (`environment.ts`)
- **Done:** `ng serve` levanta en `:4200` con página vacía

### FE-02 — Servicio HTTP base y modelos
- `ApiService` con interceptor de errores
- Modelos TypeScript de `Card`, `Deck`, `Match` (tipados estrictos)
- Servicio `CardService` que consume `/api/cards`
- **Done:** pantalla de prueba lista 5 cartas del backend

### SPIKE-01 — POC de WebSockets (crítico)
- Spike en rama `spike/websockets`
- Backend: endpoint STOMP simple que hace echo
- Frontend: cliente que se conecta y recibe mensajes
- Documentar decisiones en `docs/decisiones/ws-transport.md`
- **Done:** un ping-pong WS funcionando, conocimiento compartido con el equipo

### DOC-01 — README inicial
- Instrucciones de clonado, requisitos (Java 21, Node 20+, Docker)
- Comandos de arranque (backend, frontend, Postgres)
- Diagrama de arquitectura inicial (puede ser ASCII)
- Link a `docs/00-plan-general.md`
- **Done:** un dev nuevo puede levantar todo siguiendo el README

## Definition of Done del Sprint
- [ ] Todo el equipo puede levantar el entorno local en <15 min
- [ ] CI corre verde en `develop`
- [ ] Caché de 146 cartas xy1 en BD
- [ ] POC de WebSockets validado
- [ ] Swagger accesible
