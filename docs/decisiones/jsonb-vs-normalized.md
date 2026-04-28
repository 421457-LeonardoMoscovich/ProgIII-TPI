# Decisión — Almacenamiento de estado de partida: tablas normalizadas + log de eventos

**Fecha:** 2026-04-27  
**Estado:** Aceptado

---

## Contexto

El motor de juego necesita persistir el estado de cada partida de Pokémon TCG: cartas en juego, puntos de daño, energías adjuntas, mano de cada jugador, mazo, premios y el historial de acciones del turno. Existen dos enfoques habituales en PostgreSQL para este tipo de estado:

1. **JSONB blob:** guardar el `GameState` completo serializado como un único valor `jsonb` en una columna, actualizándolo en cada acción.
2. **Tablas normalizadas + log append-only:** modelar cada entidad como filas relacionales (`matches`, `match_events`, `decks`, `deck_cards`, etc.) y registrar cada acción del juego como una fila inmutable en `match_events`.

El proyecto también tiene el invariante arquitectónico de que **el backend es la única fuente de verdad** y que **el estado oculto del oponente (mano, orden del mazo, cartas premio) nunca debe ser enviado al cliente rival**.

---

## Decisión

Se usa el enfoque de **tablas normalizadas con log de eventos append-only**.

Estructura clave:

- `matches` — ciclo de vida de la partida (estado, jugadores, fechas).
- `match_events` — cada acción del juego (robar carta, adjuntar energía, atacar, retirar Pokémon, etc.) es una fila nueva; nunca se actualiza ni elimina.
- `decks` / `deck_cards` — composición de mazo vinculada a cada jugador.
- Flyway `V2__gin_indexes.sql` agrega índices GIN sobre columnas `jsonb` presentes en los *payloads* de eventos (atributos de carta almacenados como JSONB), no sobre el estado completo.
- El `GameEngine` reconstruye el `GameState` en memoria reproduciendo el log; no depende de tipos REST ni WebSocket (invariante de transport-agnosticism).

---

## Consecuencias

### Ventajas

- **Auditoría y replay:** el log append-only permite reconstruir cualquier momento de la partida, facilitar debugging y verificar resultados disputados.
- **Reconexión robusta (WS-03):** cuando un jugador se reconecta, el servidor reproduce el log desde el inicio para restaurar el estado exacto sin almacenar snapshots adicionales.
- **Sin riesgos de concurrencia por sobrescritura:** dos acciones simultáneas generan dos filas nuevas; no hay `UPDATE` sobre el estado completo que pueda perderse bajo alta concurrencia.
- **Consultas SQL sobre el historial:** es posible hacer `SELECT` sobre tipos de eventos, tiempos por turno o patrones de uso de cartas directamente en SQL.
- **Privacidad por diseño:** los `FilteredGameStateDto` se construyen seleccionando columnas específicas por jugador; el estado oculto del oponente nunca se incluye en la consulta al cliente rival.

### Desventajas

- **Consultas más complejas:** reconstruir el estado actual requiere agregar eventos; no es un simple `SELECT *` sobre una columna.
- **Crecimiento del log:** partidas largas acumulan muchas filas en `match_events`; requiere política de archivado a futuro.
- **Latencia de reconexión proporcional al largo de la partida:** reproducir 200 eventos tarda más que leer un snapshot; aceptable para el volumen universitario previsto.

---

## Alternativas consideradas

### JSONB blob (descartado)

Guardar el `GameState` completo como `jsonb` en `matches.state` y actualizarlo con cada acción.

- **Pros:** consulta de estado actual trivial (`SELECT state FROM matches WHERE id = ?`).
- **Contras:**
  - Sin historial de acciones; imposible replay ni auditoría.
  - `UPDATE` sobre un objeto grande bajo escrituras concurrentes de WebSocket puede producir condiciones de carrera.
  - Para exponer estado filtrado por jugador hay que deserializar en Java y filtrar en memoria, sin garantía de que campos sensibles no escapen por un bug de serialización.
  - PostgreSQL MVCC genera bloat significativo en columnas JSONB grandes que se actualizan con frecuencia.

### JSONB blob + tabla de eventos (descartado)

Combinar un snapshot JSONB actualizable con un log secundario.

- **Pros:** estado actual rápido; log disponible.
- **Contras:** dos fuentes de verdad que pueden desincronizarse; complejidad de mantenimiento doble sin beneficio claro sobre la solución elegida.
