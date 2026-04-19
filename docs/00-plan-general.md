# Plan General — Pokémon TCG (TPI Programación III)

## Contexto
TPI de 2° año, 3° cuatrimestre. Implementación full-stack del Pokémon Trading Card Game siguiendo el reglamento oficial **XY1**, consumiendo la API pública **pokemontcg.io (v2)** restringida al set `xy1` (146 cartas).

## Stack tecnológico obligatorio
| Capa | Tecnología |
|------|------------|
| Backend | Java 21 + Spring Boot 3.x |
| Build | Maven |
| Frontend | Angular 21+ con TypeScript estricto |
| Base de datos | PostgreSQL (o MySQL) |
| Tiempo real | WebSockets (Spring) |
| API externa | pokemontcg.io v2 (cache local obligatorio) |
| Testing | JUnit 5, Mockito, JaCoCo (≥80% global, ≥90% en RuleValidator/DamageCalculator/StatusEffectManager) |
| Docs API | Swagger / OpenAPI |
| VCS | Git + GitHub, workflow GitFlow |

## Arquitectura de alto nivel
```
┌──────────────┐     REST + WebSocket     ┌──────────────────┐
│  Angular SPA │ ───────────────────────► │  Spring Boot API │
└──────────────┘ ◄─────────────────────── └────────┬─────────┘
                                                   │
                                                   ▼
                                          ┌──────────────────┐
                                          │   Game Engine    │
                                          │  (aislado, puro) │
                                          └────────┬─────────┘
                                                   │
                                                   ▼
                                          ┌──────────────────┐
                                          │   PostgreSQL     │
                                          │  + Cache xy1     │
                                          └──────────────────┘
                                                   ▲
                                                   │ (solo desde Deck Builder)
                                          ┌────────┴─────────┐
                                          │  pokemontcg.io   │
                                          └──────────────────┘
```

**Invariantes:**
- El backend es la **única fuente de verdad**.
- El Game Engine no depende del transporte (REST/WebSocket).
- El motor de juego **nunca** llama a la API externa durante una partida (solo el Deck Builder).
- La mano del oponente, el orden del mazo y las cartas de Premio **jamás** se envían al cliente rival.

## Patrones de diseño aplicados (RNF-04)
| Patrón | Uso |
|--------|-----|
| **State** | Estados de partida (WAITING/SETUP/ACTIVE/FINISHED) y fases del turno (DRAW/MAIN/ATTACK/BETWEEN_TURNS) |
| **Strategy** | Efectos de ataques y de cartas de Entrenador |
| **Chain of Responsibility** | Pipeline de resolución de ataque (7 pasos de RF-01c) |
| **Observer** | Notificación de eventos de juego por WebSocket |
| **Repository** | Acceso a cartas, mazos y estado de partida |
| **Facade** | `GameEngine` expone una API simple al resto de la app |

## Organización del equipo (6–7 personas)
| Rol | Responsabilidad principal |
|-----|--------------------------|
| 2× Backend — Engine | RuleValidator, DamageCalculator, StatusEffectManager, VictoryConditionChecker, TurnManager y tests críticos |
| 2× Backend — API/Infra | REST, WebSocket, persistencia, cache pokemontcg.io |
| 2× Frontend | Deck Builder, tablero interactivo, drag & drop, WS client |
| 1× QA / DevOps | CI/CD, JaCoCo, E2E, documentación |

## Estructura de Sprints
6 sprints. Cada sprint tiene su propio documento con desglose de tareas:

- [Sprint 1 — Fundaciones](./sprints/sprint-1-fundaciones.md)
- [Sprint 2 — Deck Builder](./sprints/sprint-2-deck-builder.md)
- [Sprint 3 — Game Engine](./sprints/sprint-3-game-engine.md)
- [Sprint 4 — Gestión de partida y persistencia](./sprints/sprint-4-partida-persistencia.md)
- [Sprint 5 — Tiempo real y UI del tablero](./sprints/sprint-5-tiempo-real-ui.md)
- [Sprint 6 — Calidad y entrega](./sprints/sprint-6-calidad-entrega.md)

## Entregables obligatorios
- [ ] Repositorio Git con README.md (instalación, ejecución, stack, diagrama de arquitectura)
- [ ] Script SQL con esquema completo + migraciones versionadas + seed data
- [ ] Documentación técnica: Swagger/OpenAPI, decisiones de diseño, manual de despliegue
- [ ] Reporte JaCoCo de cobertura

## Criterios de evaluación (100 pts + 15 bonus)
| Categoría | Puntos |
|-----------|--------|
| Funcionalidad (reglas, partida completa, validaciones, condiciones, daño) | 40 |
| Arquitectura y código (separación, engine, patrones, limpieza, errores) | 25 |
| Frontend (UI funcional, WS, diseño, desktop+tablet) | 15 |
| Base de datos (modelo, queries, integridad) | 10 |
| Testing (unit ≥80/90%, integración, E2E) | 10 |
| **Opcionales (bonus)** | +15 |

## Riesgos y mitigaciones tempranas
| Riesgo | Mitigación |
|--------|-----------|
| WebSockets no se enseña en clase | Spike técnico en Sprint 1, POC simple antes de Sprint 5 |
| Ataques con efectos complejos | Patrón Strategy desde el día 1, catálogo incremental |
| Filtrado de estado por jugador (no leakear mano rival) | Diseñar DTOs con vista filtrada ya en Sprint 4 |
| Integración pokemontcg.io lenta o caída | Cache local obligatoria cargada al arranque |
| Cobertura 90% en componentes críticos | TDD estricto en Sprint 3 |
