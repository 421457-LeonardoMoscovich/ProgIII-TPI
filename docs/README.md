# Documentación — Pokémon TCG TPI

Documentación del plan de trabajo y arquitectura del TPI de Programación III (UTN FRC).

## Índice

### Plan general
- [00 — Plan General](./00-plan-general.md) — stack, arquitectura, roles, riesgos, criterios de evaluación

### Desglose por Sprint
1. [Sprint 1 — Fundaciones](./sprints/sprint-1-fundaciones.md) — infra, skeletons, cache xy1, spike WS
2. [Sprint 2 — Deck Builder](./sprints/sprint-2-deck-builder.md) — CRUD de mazos + validador oficial
3. [Sprint 3 — Game Engine](./sprints/sprint-3-game-engine.md) — motor de reglas aislado (el corazón del TP)
4. [Sprint 4 — Partida y persistencia](./sprints/sprint-4-partida-persistencia.md) — gestión de ciclo de vida + log inmutable
5. [Sprint 5 — Tiempo real y UI](./sprints/sprint-5-tiempo-real-ui.md) — WebSockets + tablero con drag & drop
6. [Sprint 6 — Calidad y entrega](./sprints/sprint-6-calidad-entrega.md) — cobertura, docs, opcionales, despliegue

## Referencia rápida

| Aspecto | Dónde |
|---------|-------|
| Stack obligatorio | [Plan General § Stack](./00-plan-general.md#stack-tecnológico-obligatorio) |
| Patrones de diseño | [Plan General § Patrones](./00-plan-general.md#patrones-de-diseño-aplicados-rnf-04) |
| RF-01 Reglas del juego | Implementación en Sprint 3 |
| RF-04 Deck Builder | Implementación en Sprint 2 |
| RF-06 WebSockets | Spike Sprint 1, implementación Sprint 5 |
| RNF-03 Cobertura | Sprint 3 (crítica) + Sprint 6 (global) |

## Entrega Sprint 6

- [Arquitectura](./arquitectura.md)
- [Manual de despliegue](./manual-despliegue.md)
- [Performance](./performance.md)

## Spec original
El PDF autoritativo está en la raíz del repo: `TUP_3C_PIII_TPI_POKEMON_TCG.pdf`.
Ante cualquier duda, prevalece el PDF sobre esta documentación.
