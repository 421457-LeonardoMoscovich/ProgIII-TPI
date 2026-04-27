# Arquitectura

## Objetivo

La aplicacion implementa Pokemon TCG XY1 como sistema full-stack. El backend es la fuente de verdad del estado de partida; el frontend solo renderiza snapshots filtrados y envia acciones.

## Capas

```text
Angular UI
  | REST: catalogo, mazos, lobby, snapshot inicial
  | STOMP/SockJS: acciones y eventos de partida
Spring Boot API
  | controllers REST
  | controllers WS
Application services
  | MatchSessionService
  | DeckService
  | CardCatalogService
Domain
  | game engine puro
  | validators y reglas
Infrastructure
  | JPA repositories
  | Pokemon TCG API client
PostgreSQL
```

## Bounded Contexts

| Contexto | Responsabilidad |
| --- | --- |
| Catalogo | Cache local del set `xy1` desde pokemontcg.io. |
| Deck Builder | Construccion, validacion y persistencia de mazos. |
| Match Lobby | Creacion, union y listado de partidas. |
| Game Engine | Reglas del turno, acciones, eventos, dano y victoria. |
| Realtime | Autenticacion STOMP, autorizacion de topics y publicacion de eventos filtrados. |

## Reglas De Dependencia

- `domain.engine` no depende de Spring, JPA, REST ni WebSocket.
- Los controllers convierten DTOs a comandos o snapshots.
- La informacion oculta del rival no se serializa en REST ni WS.
- Los topics personales usan `/topic/match/{matchId}/{userId}`.
- `/user/queue/ack` se usa para ACK/NACK de acciones.

## Decisiones Clave

- WebSocket usa STOMP sobre SockJS por integracion nativa con Spring Messaging.
- El estado vivo de partida se mantiene en memoria por sprint scope; el backend sigue siendo autoridad.
- REST `GET /api/matches/{id}/state` queda como snapshot inicial y recuperacion.
- JaCoCo excluye DTOs/config/bootstrap del gate global, pero exige umbrales altos en reglas criticas.

