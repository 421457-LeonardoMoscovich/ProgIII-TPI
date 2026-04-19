# Decisión — Transporte WebSocket

**Fecha:** 2026-04-19
**Sprint:** 1 — SPIKE-01
**Estado:** aceptada

## Contexto

El juego es turn-based pero requiere notificaciones en tiempo real a ambos jugadores (acciones del oponente, cambios de estado, fin de partida). El spec obliga al uso de **Spring WebSockets** en el backend (RNF-02).

## Opciones evaluadas

| Opción | Pros | Contras |
|--------|------|---------|
| WebSocket crudo (`@ServerEndpoint`) | Bajo overhead, simple | Sin pub/sub listo, hay que implementar fan-out manual |
| **STOMP sobre SockJS (Spring messaging)** | Broker embebido, destinos `/topic` y `/queue/<user>` listos, fallback a long-polling vía SockJS, integración nativa con Spring Security | Un poco más de peso en el frame |
| SSE | Simple, HTTP/1.1 | Unidireccional — no sirve para acciones del cliente |

## Decisión

**STOMP sobre SockJS** con broker simple en memoria (`enableSimpleBroker`).

- Destinos públicos: `/topic/match/{id}` para eventos de partida.
- Destinos privados: `/queue/user/{userId}/*` para información filtrada por jugador (manos, prizes) — **crítico** para mantener el invariante "estado oculto del oponente no se serializa al rival" (ver CLAUDE.md).
- Prefijo de entrada del cliente: `/app/*`.

Si más adelante se necesita HA, migrar a RabbitMQ/ActiveMQ como broker externo es cambio de configuración, no de código.

## POC (SPIKE-01)

- Backend: `EchoController` en `/app/echo` → `@SendTo("/topic/echo")`.
- Frontend: `WebSocketService` con `@stomp/stompjs` + `sockjs-client`, conecta a `/ws` y reenvía suscripciones como `Observable`.

## Consecuencias

- Frontend usa `@stomp/stompjs` + `sockjs-client` (ya instalados).
- CORS habilitado en `/ws` para `localhost:4200` en dev.
- El autenticación sobre STOMP se resuelve en Sprint 4 cuando llegue Spring Security.
