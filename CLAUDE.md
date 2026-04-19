# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Pokémon TCG** — TPI for Programación III (2nd year, 3rd cuatrimestre). Full-stack implementation of the Pokémon Trading Card Game following the official **XY1** ruleset, consuming **pokemontcg.io v2** restricted to set `xy1` (146 cards).

The authoritative spec is `TUP_3C_PIII_TPI_POKEMON_TCG.pdf`. Planning docs live under `docs/` — start with `docs/00-plan-general.md` and the six sprint breakdowns in `docs/sprints/`.

**Status:** Pre-code. Only specification and planning docs exist. No source tree yet.

## Mandatory Stack (from spec — not negotiable)

| Layer | Tech |
|-------|------|
| Backend | Java 21 + Spring Boot 3.x, Maven |
| Frontend | Angular 21+ (strict TypeScript) |
| DB | PostgreSQL (or MySQL) |
| Real-time | Spring WebSockets |
| External API | pokemontcg.io v2 (local cache required) |
| Testing | JUnit 5, Mockito, JaCoCo |
| API docs | Swagger / OpenAPI |
| VCS | Git + GitHub, GitFlow |

Coverage gates: **≥80% global**, **≥90% on `RuleValidator`, `DamageCalculator`, `StatusEffectManager`**.

## Architectural Invariants

These are load-bearing constraints — violating any of them breaks the grading criteria:

1. **Backend is the single source of truth.** The frontend never computes authoritative game state.
2. **Game Engine is transport-agnostic.** It must not depend on REST/WebSocket types.
3. **No external API calls during a match.** `pokemontcg.io` is touched only by the Deck Builder flow; the match engine reads from the local `xy1` cache.
4. **Opponent-hidden state never leaves the server.** The opponent's hand, deck order, and Prize cards must not be serialized into DTOs sent to the rival client. Design player-filtered DTOs from Sprint 4 onward.

## Required Design Patterns (RNF-04)

| Pattern | Where |
|---------|-------|
| State | Match lifecycle (WAITING/SETUP/ACTIVE/FINISHED) and turn phases (DRAW/MAIN/ATTACK/BETWEEN_TURNS) |
| Strategy | Attack effects and Trainer card effects |
| Chain of Responsibility | 7-step attack resolution pipeline (RF-01c) |
| Observer | WebSocket game-event notifications |
| Repository | Cards, decks, match state access |
| Facade | `GameEngine` as the entry point for the rest of the app |

## Sprint Map

Work is broken into 6 sprints — see `docs/sprints/` for full task lists:

1. Fundaciones — repo, CI, DB schema, `xy1` cache, WebSocket spike
2. Deck Builder — card browsing + deck validation against XY1 rules
3. Game Engine — core rules, damage calc, status effects, victory conditions (TDD-critical)
4. Partida & persistencia — match lifecycle, player-filtered DTOs, persistence
5. Tiempo real & UI — WebSocket wiring + interactive board
6. Calidad y entrega — E2E, coverage reports, deployment docs

## Team Layout (6–7 people)

- 2× Backend Engine (rules, damage, status, turn manager, critical tests)
- 2× Backend API/Infra (REST, WS, persistence, `pokemontcg.io` cache)
- 2× Frontend (Deck Builder, board, DnD, WS client)
- 1× QA/DevOps (CI/CD, JaCoCo, E2E, docs)

## Build & Run

*Not yet scaffolded.* Commands will be added once the Maven project (`mvnw`) and Angular workspace exist. Expected shape:

- Backend: `./mvnw spring-boot:run` / `./mvnw test` / `./mvnw test -Dtest=RuleValidatorTest`
- Frontend: `npm start` / `npm test` / `npm run lint` inside the Angular workspace
- Coverage: `./mvnw verify` produces the JaCoCo report

## Notes

- Spec PDF is authoritative — when requirements conflict with planning docs, the PDF wins.
- `nul` in the project root is an accidental artifact from a Windows redirect (`> NUL`); safe to delete.
