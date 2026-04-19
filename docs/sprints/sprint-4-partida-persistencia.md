# Sprint 4 — Gestión de partida y persistencia

**Objetivo:** integrar el Game Engine con la capa de aplicación. Crear partidas, matchmaking, persistir estado y log completo, exponer endpoints REST. El estado se puede reconstruir desde BD ante cualquier fallo.

**Duración estimada:** 2 semanas
**RF/RNF cubiertos:** RF-03, RF-05 completos. Preparación de RF-06.

## Tareas Backend

### APP-01 — `MatchService` (orquestador)
- Crea partidas (`createMatch`), asigna jugador 1
- Matchmaking: `joinMatch(matchId)` para jugador 2
- Transición WAITING → SETUP automática al unirse el 2º
- Dispara setup del engine (mulligan, colocación, premios)
- `applyAction(matchId, playerId, action)` delega en Facade + persiste
- **Done:** cubierto con tests de integración

### APP-02 — Entidades de persistencia
- `Match` (id, status, player1Id, player2Id, winnerId, createdAt, startedAt, endedAt)
- `MatchState` (matchId, turnNumber, stateJson JSONB, updatedAt) — último snapshot
- `MatchEvent` (matchId, sequenceNumber, turnNumber, playerId, eventType, payloadJson, createdAt) — inmutable
- `MatchParticipant` (matchId, playerId, deckId, side)
- **Done:** migración Flyway, repositorios JPA

### APP-03 — Serialización de `GameState`
- Serializador JSON del `GameState` del engine ↔ `MatchState.stateJson`
- Inyectable al Facade para hidratación
- Tests de round-trip (serializar → deserializar → comparar)
- **Done:** estado reconstruible sin pérdida

### APP-04 — Persistencia automática (RF-05)
Pipeline tras cada acción válida:
1. Engine emite nuevo estado + lista de eventos
2. Se persiste el snapshot (`MatchState`)
3. Se appendean los eventos (`MatchEvent`) con sequence number incremental
4. **Todo en una transacción**
- **Done:** si el server cae después de la transacción, el próximo load reconstruye perfectamente

### APP-05 — Reconstrucción de partida
- `GET /api/matches/{id}/state` devuelve el estado filtrado por jugador
- `loadMatch(matchId)` en el servicio: levanta snapshot + reinyecta al Facade
- **Tests:** matar el proceso a mitad de partida y reanudar
- **Done:** partida reanudable post-reinicio

### APP-06 — Filtrado de estado por jugador (RNF-05)
`StateProjector` que, dado el `GameState` completo y el `playerId` solicitante, devuelve un DTO:
- Mano propia visible, mano del rival solo **cantidad**
- Mazos nunca revelan orden
- Cartas de Premio: solo cantidad restante (no contenido)
- Descarte visible para ambos
- Activo, Banca, Estadio visibles para ambos
- **Tests:** asegurar que ningún campo privado filtra
- **Done:** DTO documentado y testeado

### APP-07 — Endpoints REST de partida
- `POST   /api/matches` — crear (body: deckId)
- `POST   /api/matches/{id}/join` — unirse (body: deckId)
- `GET    /api/matches/{id}/state` — snapshot actual filtrado por el requester
- `POST   /api/matches/{id}/actions` — aplicar acción
- `GET    /api/matches/{id}/events?since=N` — stream de eventos desde sequence N
- `GET    /api/matches` — listar partidas (para lobby)
- Validación: solo los jugadores de la partida pueden accionar
- **Done:** documentados en Swagger

### APP-08 — Log de acciones inmutable
- `MatchEvent` nunca se actualiza ni borra
- Sequence number por partida, monotónicamente creciente
- Útil para reconexión (el cliente pide eventos desde N)
- **Done:** constraint de BD + test que intenta modificar

### APP-09 — Manejo de acciones inválidas
- Mapear `ActionResult.rejected` → HTTP 422 con mensaje descriptivo
- Ej: "No puedes atacar: te falta 1 Energía de Fuego para usar este ataque"
- **Done:** mensajes accionables (RNF-06)

## Tareas Frontend

### FE-07 — Lobby de partidas
- Ruta `/lobby`
- Lista de partidas disponibles (estado WAITING)
- Botones: crear partida (elige mazo), unirse, ver partidas propias
- **Done:** flujo completo funcional contra REST

### FE-08 — Estado de partida (sin WS todavía)
- Ruta `/match/:id`
- Consume `GET /match/:id/state` por polling (placeholder hasta WS)
- Modelos TS que reflejan el DTO filtrado
- Vista mínima: turno actual, HP del Activo, mano propia, contadores
- **Done:** se puede ver el estado de una partida iniciada

## Tareas QA

### QA-02 — Tests de integración
- Partida completa vía REST: crear → unir → mulligan → turnos → victoria
- Reconstrucción tras "reinicio" (cerrar contexto Spring y reabrir)
- Filtrado de estado: el jugador A nunca ve la mano de B
- **Done:** verdes en CI

## Definition of Done del Sprint
- [ ] Partidas creables y joineables vía REST
- [ ] Estado persistido tras cada acción
- [ ] Partida reconstruible tras reinicio
- [ ] DTO filtrado sin leaks de información privada
- [ ] Log inmutable de eventos con sequence number
